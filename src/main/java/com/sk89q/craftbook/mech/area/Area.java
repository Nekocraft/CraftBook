package com.sk89q.craftbook.mech.area;

import java.io.IOException;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.event.player.PlayerInteractEvent;

import com.sk89q.craftbook.AbstractMechanic;
import com.sk89q.craftbook.AbstractMechanicFactory;
import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.LocalPlayer;
import com.sk89q.craftbook.SourcedBlockRedstoneEvent;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.exceptions.InvalidMechanismException;
import com.sk89q.craftbook.util.exceptions.ProcessedMechanismException;
import com.sk89q.worldedit.BlockWorldVector;
import com.sk89q.worldedit.data.DataException;

/**
 * Area.
 *
 * @author Me4502, Sk89q, Silthus
 */

public class Area extends AbstractMechanic {

    public static class Factory extends AbstractMechanicFactory<Area> {

        public Factory() {

        }

        private final CraftBookPlugin plugin = CraftBookPlugin.inst();

        /**
         * Detect the mechanic at a placed sign.
         *
         * @throws ProcessedMechanismException
         */
        @Override
        public Area detect(BlockWorldVector pt, LocalPlayer player, ChangedSign sign) throws InvalidMechanismException, ProcessedMechanismException {

            if (!plugin.getConfiguration().areaEnabled) return null;

            String[] lines = sign.getLines();

            if (lines[1].equalsIgnoreCase("[Area]") || lines[1].equalsIgnoreCase("[SaveArea]")) {
                if (sign.getLine(0).trim().isEmpty()) {
                    sign.setLine(0, "~" + player.getName());
                }
                if (lines[1].equalsIgnoreCase("[Area]")) {
                    player.checkPermission("craftbook.mech.area.sign.area");
                    sign.setLine(1, "[Area]");
                } else if (lines[1].equalsIgnoreCase("[SaveArea]")) {
                    player.checkPermission("craftbook.mech.area.sign.savearea");
                    sign.setLine(1, "[SaveArea]");
                }
                sign.update(false);
                // check if the namespace and area exists
                isValidArea(sign);
                player.print("Toggle area created.");
            } else return null;

            throw new ProcessedMechanismException();
        }

        /**
         * Explore around the trigger to find a Door; throw if things look funny.
         *
         * @param pt the trigger (should be a signpost)
         *
         * @return a Area if we could make a valid one, or null if this looked nothing like a area.
         *
         * @throws InvalidMechanismException if the area looked like it was intended to be a area, but it failed.
         */
        @Override
        public Area detect(BlockWorldVector pt) throws InvalidMechanismException {

            if (!plugin.getConfiguration().areaAllowRedstone) return null;

            Block block = BukkitUtil.toWorld(pt).getBlockAt(BukkitUtil.toLocation(pt));
            if (SignUtil.isSign(block.getTypeId())) {
                BlockState state = block.getState();
                if (state instanceof Sign) {
                    ChangedSign sign = BukkitUtil.toChangedSign((Sign) state);
                    if (sign.getLine(1).equalsIgnoreCase("[Area]") || sign.getLine(1).equalsIgnoreCase("[SaveArea]")) {
                        sign.update(false);
                        // check if the namespace and area exists
                        isValidArea(sign);
                        boolean save = sign.getLine(1).equalsIgnoreCase("[SaveArea]");
                        return new Area(pt, save);
                    }
                }
            }
            return null;
        }

        private void isValidArea(ChangedSign sign) throws InvalidMechanismException {

            String namespace = sign.getLine(0).trim();
            String areaOn = sign.getLine(2).trim().toLowerCase();
            String areaOff = sign.getLine(3).trim().toLowerCase();
            if (CopyManager.isExistingArea(plugin.getDataFolder(), namespace, areaOn)) {
                if (areaOff == null || areaOff.isEmpty() || areaOff.equals("--")) return;
                if (CopyManager.isExistingArea(plugin.getDataFolder(), namespace, areaOff)) return;
            }
            throw new InvalidMechanismException("The area or namespace does not exist.");
        }
    }

    private static CraftBookPlugin plugin = CraftBookPlugin.inst();
    private final BlockWorldVector pt;
    private boolean toggledOn;
    private boolean saveOnToggle = false;

    /**
     * Raised when a block is right clicked.
     *
     * @param event
     */
    @Override
    public void onRightClick(PlayerInteractEvent event) {

        LocalPlayer player = plugin.wrapPlayer(event.getPlayer());

        if (!player.hasPermission("craftbook.mech.area.use")) {
            player.print("mech.use-permission");
            return;
        }

        // check if the sign still exists
        Sign sign = null;
        if (BukkitUtil.toBlock(pt).getState() instanceof Sign) {
            sign = (Sign) BukkitUtil.toBlock(pt).getState();
        }
        if (sign == null) return;
        // toggle the area on or off
        toggle(BukkitUtil.toChangedSign(sign));

        event.setCancelled(true);
    }

    /**
     * Raised when an input redstone current changes.
     *
     * @param event
     */
    @Override
    public void onBlockRedstoneChange(SourcedBlockRedstoneEvent event) {

        if (!plugin.getConfiguration().areaAllowRedstone) return;

        // check if the sign still exists
        Sign sign = null;
        if (BukkitUtil.toBlock(pt).getState() instanceof Sign) {
            sign = (Sign) BukkitUtil.toBlock(pt).getState();
        }
        if (sign == null) return;
        // toggle the area
        toggle(BukkitUtil.toChangedSign(sign));
    }

    /**
     * @param pt   if you didn't already check if this is a signpost with appropriate text,
     *             you're going on Santa's naughty list.
     * @param save Whether to save on toggle or not
     *
     * @throws InvalidMechanismException
     */
    private Area(BlockWorldVector pt, boolean save) throws InvalidMechanismException {

        super();
        this.pt = pt;
        saveOnToggle = save;
    }

    public boolean isToggledOn() {

        return toggledOn;
    }

    private boolean toggle(ChangedSign sign) {

        if (!checkSign(sign)) return false;
        checkToggleState(sign);

        try {
            World world = BukkitUtil.toSign(sign).getWorld();
            String namespace = sign.getLine(0);
            String id = sign.getLine(2).replace("-", "").toLowerCase();
            String inactiveID = sign.getLine(3).replace("-", "").toLowerCase();

            CuboidCopy copy;

            if (isToggledOn()) {

                copy = CopyManager.getInstance().load(world, namespace, id);

                // if this is a save area save it before toggling off
                if (saveOnToggle) {
                    copy.copy();
                    CopyManager.getInstance().save(world, namespace, id, copy);
                }
                // if we are toggling to the second area we dont clear the old area
                if (!inactiveID.isEmpty() && !inactiveID.equals("--")) {
                    copy = CopyManager.getInstance().load(world, namespace, inactiveID);
                    copy.paste();
                } else {
                    copy.clear();
                }
                setToggledState(sign, false);
            } else {

                // toggle the area on
                // if this is a save area save it before toggling off
                if (saveOnToggle && !inactiveID.isEmpty() && !inactiveID.equals("--")) {
                    copy = CopyManager.getInstance().load(world, namespace, inactiveID);
                    copy.copy();
                    CopyManager.getInstance().save(world, namespace, inactiveID, copy);
                }

                copy = CopyManager.getInstance().load(world, namespace, id);
                copy.paste();
                setToggledState(sign, true);
            }
            return true;
        } catch (CuboidCopyException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to toggle Area: " + e.getMessage());
        } catch (DataException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to toggle Area: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to toggle Area: " + e.getMessage());
        }
        return false;
    }

    public static boolean toggleCold(ChangedSign sign) {

        if (!checkSign(sign)) return false;

        boolean toggleOn = coldCheckToggleState(sign);
        boolean save = sign.getLine(1).equalsIgnoreCase("[SaveArea]");

        try {
            World world = BukkitUtil.toSign(sign).getWorld();
            String namespace = sign.getLine(0);
            String id = sign.getLine(2).replace("-", "").toLowerCase();
            String inactiveID = sign.getLine(3).replace("-", "").toLowerCase();

            CuboidCopy copy;

            if (toggleOn) {

                copy = CopyManager.getInstance().load(world, namespace, id);

                // if this is a save area save it before toggling off
                if (save) {
                    copy.copy();
                    CopyManager.getInstance().save(world, namespace, id, copy);
                }
                // if we are toggling to the second area we dont clear the old area
                if (!inactiveID.isEmpty() && !inactiveID.equals("--")) {
                    copy = CopyManager.getInstance().load(world, namespace, inactiveID);
                    copy.paste();
                } else {
                    copy.clear();
                }
                setToggledState(sign, false);
            } else {

                // toggle the area on
                // if this is a save area save it before toggling off
                if (save && !inactiveID.isEmpty() && !inactiveID.equals("--")) {
                    copy = CopyManager.getInstance().load(world, namespace, inactiveID);
                    copy.copy();
                    CopyManager.getInstance().save(world, namespace, inactiveID, copy);
                } else
                    copy = CopyManager.getInstance().load(world, namespace, id);
                copy.paste();
                setToggledState(sign, true);
            }
            return true;
        } catch (CuboidCopyException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cold toggle Area: " + e.getMessage());
        } catch (DataException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cold toggle Area: " + e.getMessage());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to cold toggle Area: " + e.getMessage());
        }
        return false;
    }

    private static boolean checkSign(ChangedSign sign) {

        String namespace = sign.getLine(0);
        String id = sign.getLine(2).toLowerCase();

        if (id == null || id.isEmpty() || id.length() < 1) return false;
        if (namespace == null || namespace.isEmpty() || namespace.length() < 1) return false;

        return true;
    }

    // pattern to check where the markers for on and off state are
    private static final Pattern pattern = Pattern.compile("^\\-[A-Za-z0-9_]*?\\-$");

    private void checkToggleState(ChangedSign sign) {

        toggledOn = coldCheckToggleState(sign);
    }

    private static boolean coldCheckToggleState(ChangedSign sign) {

        String line3 = sign.getLine(2).toLowerCase();
        String line4 = sign.getLine(3).toLowerCase();
        return pattern.matcher(line3).matches() || !(line4.equals("--") || pattern.matcher(line4).matches());
    }

    private static void setToggledState(ChangedSign sign, boolean state) {

        int toToggleOn = state ? 2 : 3;
        int toToggleOff = state ? 3 : 2;
        sign.setLine(toToggleOff, sign.getLine(toToggleOff).replace("-", ""));
        sign.setLine(toToggleOn, "-" + sign.getLine(toToggleOn) + "-");
        sign.update(false);
    }
}
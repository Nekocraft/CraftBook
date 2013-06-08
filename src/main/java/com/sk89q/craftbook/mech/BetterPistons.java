package com.sk89q.craftbook.mech;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.PistonBaseMaterial;
import org.bukkit.util.Vector;

import com.sk89q.craftbook.AbstractMechanic;
import com.sk89q.craftbook.AbstractMechanicFactory;
import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.LocalPlayer;
import com.sk89q.craftbook.SourcedBlockRedstoneEvent;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.util.EntityUtil;
import com.sk89q.craftbook.util.LocationUtil;
import com.sk89q.craftbook.util.RegexUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.exceptions.InvalidMechanismException;
import com.sk89q.craftbook.util.exceptions.ProcessedMechanismException;
import com.sk89q.worldedit.BlockWorldVector;
import com.sk89q.worldedit.blocks.BlockID;

public class BetterPistons extends AbstractMechanic {

    public static class Factory extends AbstractMechanicFactory<BetterPistons> {

        Types type;

        public Factory(Types type) {

            this.type = type;
        }

        /**
         * Explore around the trigger to find a functional elevator; throw if things look funny.
         *
         * @param pt the trigger (should be a signpost)
         *
         * @return an Elevator if we could make a valid one, or null if this looked nothing like an elevator.
         *
         * @throws InvalidMechanismException if the area looked like it was intended to be an elevator, but it failed.
         */
        @Override
        public BetterPistons detect(BlockWorldVector pt) throws InvalidMechanismException {

            Block block = BukkitUtil.toBlock(pt);
            // check if this looks at all like something we're interested in first
            if(block.getTypeId() == BlockID.PISTON_BASE || block.getTypeId() == BlockID.PISTON_STICKY_BASE) {

                PistonBaseMaterial piston = (PistonBaseMaterial) block.getState().getData();
                Block sign = block.getRelative(piston.getFacing().getOppositeFace());
                Types type = null;
                signCheck: {
                    for(BlockFace face : LocationUtil.getDirectFaces()) {
                        if(face == piston.getFacing())
                            continue;
                        sign = block.getRelative(face);
                        type = checkSign(sign);
                        if(type == this.type)
                            break signCheck;
                        else if (type != null && SignUtil.isSign(sign.getRelative(face)) && SignUtil.getFacing(sign.getRelative(face)) == SignUtil.getFacing(sign)) {
                            sign = sign.getRelative(face);
                            type = checkSign(sign);
                            if(type == this.type)
                                break signCheck;
                        }
                    }
                }

                if(type == null || type != this.type)
                    return null;
                return new BetterPistons(block, sign, type);
            }

            return null;
        }

        public Types checkSign(Block sign) {

            Types type = null;

            if(sign.getState() instanceof Sign) {

                Sign s = (Sign) sign.getState();
                if(s.getLine(1).equalsIgnoreCase("[Crush]") && CraftBookPlugin.inst().getConfiguration().pistonsCrusher) {
                    s.setLine(1, "[Crush]");
                    s.update(true);
                    type = Types.CRUSH;
                }
                if(s.getLine(1).equalsIgnoreCase("[SuperSticky]") && CraftBookPlugin.inst().getConfiguration().pistonsSuperSticky) {
                    s.setLine(1, "[SuperSticky]");
                    s.update(true);
                    type = Types.SUPERSTICKY;
                }
                if(s.getLine(1).equalsIgnoreCase("[Bounce]") && CraftBookPlugin.inst().getConfiguration().pistonsBounce) {
                    s.setLine(1, "[Bounce]");
                    s.update(true);
                    type = Types.BOUNCE;
                }
                if(s.getLine(1).equalsIgnoreCase("[SuperPush]") && CraftBookPlugin.inst().getConfiguration().pistonsSuperPush) {
                    s.setLine(1, "[SuperPush]");
                    s.update(true);
                    type = Types.SUPERPUSH;
                }
            }

            return type;
        }

        /**
         * Detect the mechanic at a placed sign.
         *
         * @throws ProcessedMechanismException
         */
        @Override
        public BetterPistons detect(BlockWorldVector pt, LocalPlayer player,
                ChangedSign sign) throws InvalidMechanismException,
                ProcessedMechanismException {

            Block block = SignUtil.getBackBlock(BukkitUtil.toSign(sign).getBlock());
            Types type = null;
            // check if this looks at all like something we're interested in first
            if(block.getTypeId() == BlockID.PISTON_BASE || block.getTypeId() == BlockID.PISTON_STICKY_BASE) {

                type = checkSign(BukkitUtil.toSign(sign).getBlock());

                if(type == null || type != this.type)
                    return null;

                player.checkPermission("craftbook.mech.pistons." + type.name().toLowerCase());

                player.print("mech.pistons." + type.name().toLowerCase() + ".created");

                throw new ProcessedMechanismException();
            }

            return null;
        }
    }

    /**
     * @param The piston triggering.
     * @param The type of piston mechanic this is.
     * 
     * @throws InvalidMechanismException
     */
    private BetterPistons(Block trigger, Block sign, Types type) throws InvalidMechanismException {

        super();
        this.trigger = trigger;
        this.sign = sign;
        this.type = type;
    }

    private double movemod = 1.0;

    /**
     * Raised when an input redstone current changes.
     */
    @Override
    public void onBlockRedstoneChange(SourcedBlockRedstoneEvent event) {

        if(event.getBlock().getTypeId() != trigger.getTypeId()) //Make sure same type (Lazy checks)
            return;

        if(type == Types.CRUSH && event.getNewCurrent() > event.getOldCurrent()) {
            PistonBaseMaterial piston = (PistonBaseMaterial) trigger.getState().getData();
            piston.setPowered(false);
            if(!canPistonPushBlock(trigger.getRelative(piston.getFacing())) || CraftBookPlugin.inst().getConfiguration().pistonsCrusherBlacklist.contains(trigger.getRelative(piston.getFacing()).getTypeId()))
                return;
            trigger.getRelative(piston.getFacing()).breakNaturally();
            trigger.getRelative(piston.getFacing()).setTypeId(0, false);

            if(CraftBookPlugin.inst().getConfiguration().pistonsCrusherInstaKill) {
                for(Entity ent : trigger.getRelative(piston.getFacing()).getChunk().getEntities()) {
                    if(EntityUtil.isEntityInBlock(ent, trigger.getRelative(piston.getFacing()))) {
                        EntityUtil.killEntity(ent);
                    }
                }
            }
        } else if(type == Types.BOUNCE && event.getNewCurrent() > event.getOldCurrent()) {
            PistonBaseMaterial piston = (PistonBaseMaterial) trigger.getState().getData();
            if(piston.isSticky())
                return;

            double mult = 1;
            try {
                mult = Double.parseDouble(((Sign) sign.getState()).getLine(2));
            }
            catch(Exception e){
                mult = 1;
            }

            Vector vel = new Vector(piston.getFacing().getModX()*mult, piston.getFacing().getModY()*mult, piston.getFacing().getModZ()*mult);
            if(trigger.getRelative(piston.getFacing()).getTypeId() == 0 || trigger.getRelative(piston.getFacing()).getState() != null && trigger.getRelative(piston.getFacing()).getState() instanceof InventoryHolder || trigger.getRelative(piston.getFacing()).getTypeId() == BlockID.PISTON_MOVING_PIECE || trigger.getRelative(piston.getFacing()).getTypeId() == BlockID.PISTON_EXTENSION || CraftBookPlugin.inst().getConfiguration().pistonsBounceBlacklist.contains(trigger.getRelative(piston.getFacing()).getTypeId())) {
                for(Entity ent : trigger.getRelative(piston.getFacing()).getChunk().getEntities()) {
                    if(EntityUtil.isEntityInBlock(ent, trigger.getRelative(piston.getFacing()))) {
                        ent.setVelocity(ent.getVelocity().add(vel));
                    }
                }
            } else {
                FallingBlock fall = trigger.getWorld().spawnFallingBlock(trigger.getRelative(piston.getFacing()).getLocation().add(vel), trigger.getRelative(piston.getFacing()).getTypeId(), trigger.getRelative(piston.getFacing()).getData());
                trigger.getRelative(piston.getFacing()).setTypeId(0);
                fall.setVelocity(vel);
            }
        } else if (type == Types.SUPERSTICKY && event.getNewCurrent() < event.getOldCurrent()) {
            final PistonBaseMaterial piston = (PistonBaseMaterial) trigger.getState().getData();
            if(!piston.isSticky())
                return;
            if(trigger.getRelative(piston.getFacing()).getTypeId() == BlockID.PISTON_EXTENSION || trigger.getRelative(piston.getFacing()).getTypeId() == BlockID.PISTON_MOVING_PIECE) {

                int block = 10;
                int amount = 1;
                try {
                    block = Integer.parseInt(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2))[0]);
                    if(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2)).length > 1)
                        amount = Integer.parseInt(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2))[1]);
                }
                catch(Exception e){
                }

                final boolean air = ((Sign) sign.getState()).getLine(3).equalsIgnoreCase("AIR");

                if(block > CraftBookPlugin.inst().getConfiguration().pistonMaxDistance)
                    block = CraftBookPlugin.inst().getConfiguration().pistonMaxDistance;

                final int fblock = block;

                for(int p = 0; p < amount; p++) {
                    final int fp = p;

                    Bukkit.getScheduler().runTaskLater(CraftBookPlugin.inst(), new Runnable() {

                        @Override
                        public void run () {
                            for(int x = 1; x <= fblock+2; x++) {
                                int i = x;
                                if(x == 1 && !(trigger.getRelative(piston.getFacing(), i).getState() instanceof InventoryHolder) && fp == 0) {
                                    x = i = 2;
                                }
                                if(x >= fblock+2 || trigger.getRelative(piston.getFacing(), i+1).getTypeId() == 0 && !air || !canPistonPushBlock(trigger.getRelative(piston.getFacing(), i+1))) {
                                    trigger.getRelative(piston.getFacing(), i).setTypeId(0);
                                    break;
                                }
                                for(Entity ent : trigger.getRelative(piston.getFacing(), i).getChunk().getEntities()) {

                                    if(EntityUtil.isEntityInBlock(ent, trigger.getRelative(piston.getFacing(), i))) {
                                        ent.teleport(ent.getLocation().subtract(piston.getFacing().getModX() * movemod, piston.getFacing().getModY() * movemod, piston.getFacing().getModZ() * movemod));
                                    }
                                }
                                copyData(trigger.getRelative(piston.getFacing(), i+1), trigger.getRelative(piston.getFacing(), i));
                            }
                        }
                    }, 2L*(p+1));
                }
            }
        } else if (type == Types.SUPERPUSH && event.getNewCurrent() > event.getOldCurrent()) {
            final PistonBaseMaterial piston = (PistonBaseMaterial) trigger.getState().getData();
            if(trigger.getRelative(piston.getFacing()).getTypeId() != BlockID.PISTON_EXTENSION && trigger.getRelative(piston.getFacing()).getTypeId() != BlockID.PISTON_MOVING_PIECE) {

                int block = 10;
                int amount = 1;
                try {
                    block = Integer.parseInt(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2))[0]);
                    if(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2)).length > 1)
                        amount = Integer.parseInt(RegexUtil.MINUS_PATTERN.split(((Sign) sign.getState()).getLine(2))[1]);
                }
                catch(Exception e){
                }

                if(block > CraftBookPlugin.inst().getConfiguration().pistonMaxDistance)
                    block = CraftBookPlugin.inst().getConfiguration().pistonMaxDistance;

                final int fblock = block;

                for(int p = 0; p < amount; p++) {
                    Bukkit.getScheduler().runTaskLater(CraftBookPlugin.inst(), new Runnable() {

                        @Override
                        public void run () {
                            for(int x = fblock+2; x >= 1; x--) {
                                final int i = x;
                                if(trigger.equals(trigger.getRelative(piston.getFacing(), i)) || trigger.getRelative(piston.getFacing(), i).getTypeId() == BlockID.PISTON_MOVING_PIECE || trigger.getRelative(piston.getFacing(), i).getTypeId() == BlockID.PISTON_EXTENSION || !canPistonPushBlock(trigger.getRelative(piston.getFacing(), i)))
                                    continue;
                                if(trigger.getRelative(piston.getFacing(), i+1).getTypeId() == 0) {
                                    for(Entity ent : trigger.getRelative(piston.getFacing(), i+1).getChunk().getEntities()) {

                                        if(EntityUtil.isEntityInBlock(ent, trigger.getRelative(piston.getFacing(), i+1))) {
                                            ent.teleport(ent.getLocation().add(piston.getFacing().getModX() * movemod, piston.getFacing().getModY() * movemod, piston.getFacing().getModZ() * movemod));
                                        }
                                    }
                                    copyData(trigger.getRelative(piston.getFacing(), i), trigger.getRelative(piston.getFacing(), i+1));
                                    trigger.getRelative(piston.getFacing(), i).setTypeId(0);
                                }
                            }
                        }

                    }, 2L*(p+1));
                }
            }
        }
    }

    /**
     * Used for moving a block to elsewhere.
     * 
     * @param from The from block.
     * @param to The block the data is being moved to.
     */
    public void copyData(Block from, Block to) {

        if(from.getState() instanceof DoubleChest || to.getState() instanceof DoubleChest)
            return;

        int type = from.getTypeId();
        byte data = from.getData();

        ItemStack[] oldInventory = null;
        if (from.getState() instanceof InventoryHolder) {
            oldInventory = ((InventoryHolder) from.getState()).getInventory().getContents().clone();
            InventoryHolder fromState = (InventoryHolder) from.getState();
            fromState.getInventory().clear();
            ((BlockState) fromState).update();
            from.setTypeId(0);
        }
        to.setTypeIdAndData(type, data, true);
        if(to.getTypeId() == BlockID.STONE_BUTTON || to.getTypeId() == BlockID.WOODEN_BUTTON) {
            if((to.getData() & 0x8) == 0x8)
                to.setData((byte) (to.getData() ^ 0x8));
        }

        if(to.getState() instanceof Sign) {
            Sign state = (Sign) to.getState();
            for(int i = 0; i < 4; i++)
                state.setLine(i, ((Sign) from.getState()).getLine(i));
            state.update();
        } else if (to.getState() instanceof InventoryHolder) {
            InventoryHolder state = (InventoryHolder) to.getState();
            state.getInventory().setContents(oldInventory);
            ((BlockState) state).update(true);
        }
    }

    public boolean canPistonPushBlock(Block block) {

        if(block.getState() instanceof DoubleChest)
            return false;

        switch(block.getTypeId()) {

            case BlockID.BEDROCK:
            case BlockID.OBSIDIAN:
            case BlockID.PISTON_MOVING_PIECE:
                return false;
            default:
                return true;
        }
    }

    private final Block trigger;
    private final Block sign;
    private final Types type;

    public static enum Types {

        CRUSH, SUPERSTICKY, BOUNCE, SUPERPUSH;

        public static boolean isEnabled(Types type) {

            if(!CraftBookPlugin.inst().getConfiguration().pistonsEnabled)
                return false;
            if(type == CRUSH)
                return CraftBookPlugin.inst().getConfiguration().pistonsCrusher;
            else if(type == SUPERSTICKY)
                return CraftBookPlugin.inst().getConfiguration().pistonsSuperSticky;
            else if(type == BOUNCE)
                return CraftBookPlugin.inst().getConfiguration().pistonsBounce;
            else if(type == SUPERPUSH)
                return CraftBookPlugin.inst().getConfiguration().pistonsSuperPush;
            else
                return CraftBookPlugin.inst().getConfiguration().pistonsEnabled;
        }
    }
}
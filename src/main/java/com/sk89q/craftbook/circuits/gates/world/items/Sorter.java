package com.sk89q.craftbook.circuits.gates.world.items;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.circuits.Pipes;
import com.sk89q.craftbook.circuits.ic.AbstractICFactory;
import com.sk89q.craftbook.circuits.ic.AbstractSelfTriggeredIC;
import com.sk89q.craftbook.circuits.ic.ChipState;
import com.sk89q.craftbook.circuits.ic.IC;
import com.sk89q.craftbook.circuits.ic.ICFactory;
import com.sk89q.craftbook.circuits.ic.PipeInputIC;
import com.sk89q.craftbook.util.InventoryUtil;
import com.sk89q.craftbook.util.ItemUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.worldedit.BlockWorldVector;

public class Sorter extends AbstractSelfTriggeredIC implements PipeInputIC {

    public Sorter(Server server, ChangedSign sign, ICFactory factory) {

        super(server, sign, factory);
    }

    Block chestBlock;
    boolean inverted;

    @Override
    public void load() {

        chestBlock = getBackBlock().getRelative(0, 1, 0);
        inverted = getSign().getLine(2).equalsIgnoreCase("invert");
    }

    @Override
    public String getTitle() {

        return "Sorter";
    }

    @Override
    public String getSignTitle() {

        return "SORTER";
    }

    @Override
    public void trigger(ChipState chip) {

        if (chip.getInput(0)) chip.setOutput(0, sort());
    }

    @Override
    public void think(ChipState state) {

        state.setOutput(0, sort());
    }

    public boolean sort() {

        boolean returnValue = false;

        for (Item item : ItemUtil.getItemsAtBlock(BukkitUtil.toSign(getSign()).getBlock())) {
            if(sortItemStack(item.getItemStack())) {
                item.remove();
                returnValue = true;
            }
        }
        return returnValue;
    }

    public boolean sortItemStack(final ItemStack item) {

        BlockFace back = SignUtil.getBack(BukkitUtil.toSign(getSign()).getBlock());
        Block b;

        if (isInAboveContainer(item) ^ inverted)
            b = SignUtil.getRightBlock(BukkitUtil.toSign(getSign()).getBlock()).getRelative(back);
        else
            b = SignUtil.getLeftBlock(BukkitUtil.toSign(getSign()).getBlock()).getRelative(back);

        Pipes pipes = Pipes.Factory.setupPipes(b, getBackBlock(), item);

        if(pipes == null)
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
        else if(!pipes.getItems().isEmpty())
            return false;

        return true;
    }

    public boolean isInAboveContainer(ItemStack item) {

        if (chestBlock.getState() instanceof InventoryHolder)
            return InventoryUtil.doesInventoryContain(((InventoryHolder) chestBlock.getState()).getInventory(), false, new ItemStack(item.getTypeId(), 1, item.getDurability()));
        else return false;
    }

    public static class Factory extends AbstractICFactory {

        public Factory(Server server) {

            super(server);
        }

        @Override
        public IC create(ChangedSign sign) {

            return new Sorter(getServer(), sign, this);
        }

        @Override
        public String getShortDescription() {

            return "Sorts items and spits out left/right depending on above chest.";
        }

        @Override
        public String[] getLineHelp() {

            return new String[] {"invert - to invert output sides", null};
        }
    }

    @Override
    public List<ItemStack> onPipeTransfer(BlockWorldVector pipe, List<ItemStack> items) {

        List<ItemStack> leftovers = new ArrayList<ItemStack>();

        for (ItemStack item : items)
            if (ItemUtil.isStackValid(item))
                if(!sortItemStack(item))
                    leftovers.add(item);

        return leftovers;
    }
}
package com.sk89q.craftbook.circuits.gates.logic;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.circuits.ic.AbstractIC;
import com.sk89q.craftbook.circuits.ic.AbstractICFactory;
import com.sk89q.craftbook.circuits.ic.ChipState;
import com.sk89q.craftbook.circuits.ic.IC;
import com.sk89q.craftbook.circuits.ic.ICFactory;
import com.sk89q.craftbook.circuits.ic.ICVerificationException;

/**
 * @author Silthus
 */
public class Delayer extends AbstractIC {

    private BukkitTask taskId;
    private long delay = 1;
    private boolean tickDelay;
    private boolean stayOnLow;

    public Delayer(Server server, ChangedSign block, ICFactory factory) {

        super(server, block, factory);
        try {
        } catch (Exception ignored) {
        }
    }

    @Override
    public void load() {
        delay = Long.parseLong(getSign().getLine(2));
        tickDelay = Boolean.parseBoolean(getSign().getLine(3).split(":")[0]);
        if(getLine(3).contains(":"))
            stayOnLow = Boolean.parseBoolean(getSign().getLine(3).split(":")[1]);
    }

    @Override
    public String getTitle() {

        return "Delayer";
    }

    @Override
    public String getSignTitle() {

        return "DELAYER";
    }

    @Override
    public void trigger(final ChipState chip) {

        long tdelay = delay * 20;
        if (tickDelay) tdelay = delay;
        if (chip.getInput(0)) {
            taskId = Bukkit.getScheduler().runTaskLater(CraftBookPlugin.inst(), new Runnable() {

                @Override
                public void run() {

                    if (chip.getInput(0)) {
                        chip.setOutput(0, true);
                    }
                }
            }, tdelay);
        } else {
            if(taskId != null && !stayOnLow)
                taskId.cancel();
            chip.setOutput(0, false);
        }
    }

    public static class Factory extends AbstractICFactory {

        public Factory(Server server) {

            super(server);
        }

        @Override
        public IC create(ChangedSign sign) {

            return new Delayer(getServer(), sign, this);
        }

        @Override
        public void verify(ChangedSign sign) throws ICVerificationException {

            try {
                Integer.parseInt(sign.getLine(2));
            } catch (Exception ignored) {
                throw new ICVerificationException("The third line needs to be a number.");
            }
        }

        @Override
        public String getShortDescription() {

            return "Delays signal by X seconds (or ticks if set).";
        }

        @Override
        public String[] getLineHelp() {

            return new String[] {"seconds", "true to use ticks:true to continue on low"};
        }
    }
}

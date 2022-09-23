package net.pl3x.map.render.job.progress;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.pl3x.map.command.Sender;
import net.pl3x.map.configuration.Lang;
import net.pl3x.map.render.job.Render;

public class Progress implements Runnable {
    private final Render render;
    private final net.pl3x.map.render.job.progress.CPSTracker cpsTracker = new CPSTracker();

    private final AtomicLong processedChunks = new AtomicLong(0);
    private final AtomicLong processedRegions = new AtomicLong(0);

    private long prevProcessedChunks = 0;
    private long totalChunks;
    private long totalRegions;
    private float percent;
    private double cps;
    private String eta = Lang.PROGRESS_ETA_UNKNOWN;
    private int stallCounter = 0;

    private final Set<Sender> senders = new HashSet<>();
    private final ProgressBossbar bossbar;

    public Progress(Render render) {
        this.render = render;
        this.bossbar = new ProgressBossbar(this);
    }

    public Render getRender() {
        return this.render;
    }

    public void showChat(Sender audience) {
        this.senders.add(audience);
    }

    public boolean hideChat(Sender audience) {
        return this.senders.remove(audience);
    }

    public ProgressBossbar getBossbar() {
        return this.bossbar;
    }

    public float getPercent() {
        return this.percent;
    }

    public double getCPS() {
        return this.cps;
    }

    public String getETA() {
        return this.eta;
    }

    public long getTotalChunks() {
        return this.totalChunks;
    }

    public void setTotalChunks(long totalChunks) {
        this.totalChunks = totalChunks;
    }

    public long getTotalRegions() {
        return this.totalRegions;
    }

    public void setTotalRegions(long totalRegions) {
        this.totalRegions = totalRegions;
    }

    public AtomicLong getProcessedChunks() {
        return this.processedChunks;
    }

    public void setProcessedChunks(long processedChunks) {
        getProcessedChunks().set(processedChunks);
        this.prevProcessedChunks = processedChunks;
    }

    public AtomicLong getProcessedRegions() {
        return this.processedRegions;
    }

    public void setProcessedRegions(long processedRegions) {
        getProcessedRegions().set(processedRegions);
    }

    public void finish() {
        getRender().getScheduledProgress().cancel(false);
        if (this.render.getWorld().hasActiveRender()) {
            this.render.getWorld().finishRender();
            getBossbar().finish();
        } else {
            getBossbar().hideAll();
        }
    }

    @Override
    public void run() {
        try {
            runProgress();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void runProgress() {
        if (this.render.getWorld().isPaused()) {
            return;
        }

        long processedChunks = getProcessedChunks().get();
        this.cpsTracker.add(processedChunks - this.prevProcessedChunks);
        this.prevProcessedChunks = processedChunks;
        this.percent = ((float) processedChunks / (float) getTotalChunks()) * 100.0F;
        this.cps = this.cpsTracker.average();
        if (this.cps > 0) {
            long timeLeft = (this.totalChunks - processedChunks) / (long) this.cps * 1000;
            this.eta = formatMilliseconds(timeLeft);
            this.stallCounter = 0;
        } else {
            this.eta = Lang.PROGRESS_ETA_UNKNOWN;
            this.stallCounter++;
        }

        // check for stalled tasks
        if (this.stallCounter > 10) {
            getRender().getStarter().send(Lang.ERROR_RENDER_STALLED);
            getRender().getWorld().cancelRender(false);
            return;
        }

        // show progress to listeners
        Component component = Lang.parse(Lang.PROGRESS_CHAT,
                Placeholder.unparsed("world", this.render.getWorld().getName()),
                Placeholder.unparsed("processed_chunks", Long.toString(processedChunks)),
                Placeholder.unparsed("total_chunks", Long.toString(getTotalChunks())),
                Placeholder.unparsed("percent", String.format("%.2f", getPercent())),
                Placeholder.unparsed("cps", String.format("%.2f", getCPS())),
                Placeholder.unparsed("eta", getETA())
        );
        for (Sender sender : this.senders) {
            sender.send(component);
        }

        // show to player bossbars
        getBossbar().update();

        // check if finished
        if (this.processedRegions.get() >= this.totalRegions) {
            finish();
        }
    }

    public static String formatMilliseconds(long time) {
        int hrs = (int) TimeUnit.MILLISECONDS.toHours(time);
        int min = (int) TimeUnit.MILLISECONDS.toMinutes(time) % 60;
        int sec = (int) TimeUnit.MILLISECONDS.toSeconds(time) % 60;
        if (hrs > 0) {
            return String.format("%dh %dm %ds", hrs, min, sec);
        } else if (min > 0) {
            return String.format("%dm %ds", min, sec);
        } else {
            return String.format("%ds", sec);
        }
    }
}

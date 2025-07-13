package gg.grumble.client.controllers;

import gg.grumble.client.utils.Closeable;
import gg.grumble.client.utils.StageAware;
import gg.grumble.client.utils.WindowIcon;
import gg.grumble.core.client.MumbleClient;
import gg.grumble.core.client.MumbleEventListener;
import gg.grumble.core.client.MumbleEvents;
import gg.grumble.core.models.MumbleUser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
@WindowIcon("/icons/info.png")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class UserStatsController implements StageAware, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(UserStatsController.class);

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MumbleUserStatsTicker");
                t.setDaemon(true);
                return t;
            });

    @FXML
    public Label pingsReceivedTcp;
    @FXML
    public Label pingsReceivedUdp;
    @FXML
    public Label averagePingTcp;
    @FXML
    public Label averagePingUdp;
    @FXML
    public Label pingDeviationTcp;
    @FXML
    public Label pingDeviationUdp;
    @FXML
    public Label fromGood;
    @FXML
    public Label fromLate;
    @FXML
    public Label fromLatePercent;
    @FXML
    public Label fromLost;
    @FXML
    public Label fromLostPercent;
    @FXML
    public Label fromResync;
    @FXML
    public Label toGood;
    @FXML
    public Label toLate;
    @FXML
    public Label toLatePercent;
    @FXML
    public Label toLost;
    @FXML
    public Label toLostPercent;
    @FXML
    public Label toResync;
    @FXML
    public Label connectionTime;
    @FXML
    public Label bandwidth;

    private Stage stage;
    private MumbleClient client;
    private MumbleUser user;
    private boolean requested = false;
    private MumbleEventListener<MumbleEvents.UserStats> statusListener;

    private ScheduledFuture<?> ticker;

    @Override
    public void close() {
        if (ticker != null) {
            ticker.cancel(false);
            ticker = null;
        }
        if (client != null && statusListener != null) {
            client.removeEventListener(MumbleEvents.UserStats.class, statusListener);
        }
        stage = null;
        client = null;
        user = null;
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void tick() {
        if (requested) return;
        requested = true;
        if (user != null) {
            user.requestStats(true);
        }
    }

    public void setClientSession(MumbleClient client, MumbleUser user) {
        this.client = client;
        this.user = user;
        // Register a listener that only cares about stats for the users session
        statusListener = client.addEventListenerFiltered(MumbleEvents.UserStats.class,
                ev -> ev.stats().getSession() == user.getSession(),
                this::updateStatsLater);
        // Request stats every 6 seconds
        ticker = scheduler.scheduleAtFixedRate(this::tick, 0, 6, TimeUnit.SECONDS);
    }

    private void updateStatsLater(MumbleEvents.UserStats userStats) {
        Platform.runLater(() -> updateStats(userStats));
    }

    private void updateStats(MumbleEvents.UserStats userStats) {
        requested = false;
        if (user != null) {
            if (stage != null) {
                stage.setTitle(user.getName());
            }

            var s = userStats.stats();

            pingsReceivedTcp.setText(Integer.toString(s.getTcpPackets()));
            pingsReceivedUdp.setText(Integer.toString(s.getUdpPackets()));

            averagePingTcp.setText(String.format("%.2f ms", s.getTcpPingAvg()));
            averagePingUdp.setText(String.format("%.2f ms", s.getUdpPingAvg()));

            pingDeviationTcp.setText(String.format("%.2f", s.getTcpPingVar()));
            pingDeviationUdp.setText(String.format("%.2f", s.getUdpPingVar()));

            var from = s.getFromClient();
            int fromTotal = from.getGood() + from.getLate() + from.getLost() + from.getResync();

            fromGood.setText(Integer.toString(from.getGood()));
            fromLate.setText(Integer.toString(from.getLate()));
            fromLost.setText(Integer.toString(from.getLost()));
            fromResync.setText(Integer.toString(from.getResync()));

            fromLatePercent.setText(String.format("%.2f", 100f * from.getLate() / fromTotal));
            fromLostPercent.setText(String.format("%.2f", 100f * from.getLost() / fromTotal));

            var to = s.getFromServer();
            int toTotal = to.getGood() + to.getLate() + to.getLost() + to.getResync();

            toGood.setText(Integer.toString(to.getGood()));
            toLate.setText(Integer.toString(to.getLate()));
            toLost.setText(Integer.toString(to.getLost()));
            toResync.setText(Integer.toString(to.getResync()));

            toLatePercent.setText(String.format("%.2f", 100f * to.getLate() / toTotal));
            toLostPercent.setText(String.format("%.2f", 100f * to.getLost() / toTotal));

            connectionTime.setText(formatDuration(s.getOnlinesecs(), s.getIdlesecs()));

            long bytesPerSec = s.getBandwidth();
            double kbitPerSec = bytesPerSec * 8.0 / 1000.0;
            bandwidth.setText(String.format("%.1f kbit/s", kbitPerSec));
        }
    }

    private String formatDuration(long totalSecs, long idleSecs) {
        long days = totalSecs / 86_400;
        long hours = (totalSecs % 86_400) / 3_600;
        long minutes = (totalSecs % 3_600) / 60;
        long seconds = totalSecs % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s ");

        sb.append("(");
        sb.append(idleSecs);
        sb.append(" idle)");

        String result = sb.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }
}

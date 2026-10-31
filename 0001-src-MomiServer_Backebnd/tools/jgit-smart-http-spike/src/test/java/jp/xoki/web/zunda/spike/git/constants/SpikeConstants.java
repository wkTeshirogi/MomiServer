package jp.xoki.web.zunda.spike.git.constants;

/** Values shared only by the isolated Smart HTTP experiment. */
public final class SpikeConstants {
    public static final String REPOSITORY_NAME = "probe.git";
    public static final String MAIN_REF = "refs/heads/main";
    public static final String TOPIC_REF = "refs/heads/topic";
    public static final String UPLOAD_PAUSED_ATTRIBUTE = "fixture.uploadPaused";
    public static final int COMMAND_TIMEOUT_SECONDS = 45;
    public static final int GATE_TIMEOUT_SECONDS = 15;

    private SpikeConstants() { }
}

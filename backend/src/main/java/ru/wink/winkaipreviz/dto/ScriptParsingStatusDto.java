package ru.wink.winkaipreviz.dto;

/**
 * Aggregated parsing status for a script across all its scenes.
 */
public class ScriptParsingStatusDto {

    private String scriptId;
    private String scriptStatus;

    private long totalScenes;
    private long pendingScenes;
    private long processingScenes;
    private long parsedScenes;
    private long failedScenes;

    private double completionPercent;

    public String getScriptId() {
        return scriptId;
    }

    public void setScriptId(String scriptId) {
        this.scriptId = scriptId;
    }

    public String getScriptStatus() {
        return scriptStatus;
    }

    public void setScriptStatus(String scriptStatus) {
        this.scriptStatus = scriptStatus;
    }

    public long getTotalScenes() {
        return totalScenes;
    }

    public void setTotalScenes(long totalScenes) {
        this.totalScenes = totalScenes;
    }

    public long getPendingScenes() {
        return pendingScenes;
    }

    public void setPendingScenes(long pendingScenes) {
        this.pendingScenes = pendingScenes;
    }

    public long getProcessingScenes() {
        return processingScenes;
    }

    public void setProcessingScenes(long processingScenes) {
        this.processingScenes = processingScenes;
    }

    public long getParsedScenes() {
        return parsedScenes;
    }

    public void setParsedScenes(long parsedScenes) {
        this.parsedScenes = parsedScenes;
    }

    public long getFailedScenes() {
        return failedScenes;
    }

    public void setFailedScenes(long failedScenes) {
        this.failedScenes = failedScenes;
    }

    public double getCompletionPercent() {
        return completionPercent;
    }

    public void setCompletionPercent(double completionPercent) {
        this.completionPercent = completionPercent;
    }
}



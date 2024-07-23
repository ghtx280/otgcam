package com.example.otgcam;

public class CameraConfig {
    private int frameRate;
    private int width;
    private int height;
    private boolean isRunning;

    public CameraConfig(int frameRate, int width, int height) {
        this.frameRate = frameRate;
        this.width = width;
        this.height = height;
        this.isRunning = false;
    }

    public int getFrameRate() { return frameRate; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { isRunning = running; }
}
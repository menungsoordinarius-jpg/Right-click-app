package com.example;

interface IInputService {
    void click(float x, float y, int button);
    void hold(float x, float y, int button, long durationMs);
    void move(float x, float y);
    void pressDown(float x, float y, int button);
    void moveHeld(float x, float y, int button);
    void release(float x, float y, int button);
    void scroll(float x, float y, int notches);
    void resetDiagnostics();
}

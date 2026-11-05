package com.example;

interface IInputService {
    void click(float x, float y, int button);
    void hold(float x, float y, int button, long durationMs);
    void move(float x, float y);
}

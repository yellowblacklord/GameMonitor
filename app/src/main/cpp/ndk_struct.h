#ifndef NDK_STRUCT_H
#define NDK_STRUCT_H

struct ObjectBox {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int label; // 0敌人, 1头, 2友军, 3人机, 4倒地, 5靶场, 6靶场头
};

#endif // NDK_STRUCT_H

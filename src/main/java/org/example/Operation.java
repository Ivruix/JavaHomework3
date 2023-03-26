package org.example;

import java.util.List;

// Класс записи операции
public record Operation(int type, int equipment, double time, int asyncPoint, List<Pair<Integer, Double>> products) {
}

package com.mirkocaserta.example;

import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.mirkocaserta.example.AirQualityIndexCalculator.airQualityIndex;
import static com.mirkocaserta.example.AirQualityIndexCalculator.mostRecent;
import static java.util.Comparator.comparing;

public class AirQualityIndexCollector
        implements Collector<TypedTimeValue, List<TypedTimeValue>, List<TimeValue>> {

    private final double maxTemperature;

    private AirQualityIndexCollector(double maxTemperature) {
        this.maxTemperature = maxTemperature;
    }

    public static AirQualityIndexCollector toUnmodifiableList(double maxTemperature) {
        return new AirQualityIndexCollector(maxTemperature);
    }

    @Override
    public Supplier<List<TypedTimeValue>> supplier() {
        return () -> Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public BiConsumer<List<TypedTimeValue>, TypedTimeValue> accumulator() {
        return List::add;
    }

    @Override
    public BinaryOperator<List<TypedTimeValue>> combiner() {
        return (typedTimeValues, typedTimeValues2) -> {
            typedTimeValues.addAll(typedTimeValues2);
            return typedTimeValues;
        };
    }

    @Override
    public Function<List<TypedTimeValue>, List<TimeValue>> finisher() {
        final Map<Instant, TimeValue> aqiAccumulator = new HashMap<>();

        return accumulator -> {
            accumulator.stream()
                    .map(TypedTimeValue::timestamp)
                    .sorted()
                    .forEach(entryTS -> {
                        final TimeValue lastTemperature = getClosest(accumulator, TypedTimeValue.Type.T, entryTS);
                        final TimeValue lastCarbonMonoxidePercentage = getClosest(accumulator, TypedTimeValue.Type.C, entryTS);

                        if (lastTemperature != null && lastCarbonMonoxidePercentage != null) {
                            Instant timestamp = mostRecent(lastTemperature.timestamp(), lastCarbonMonoxidePercentage.timestamp());
                            aqiAccumulator.put(timestamp, TimeValue.of(timestamp, airQualityIndex(lastTemperature.value(), lastCarbonMonoxidePercentage.value(), maxTemperature)));
                        }
                    });

            return aqiAccumulator.values().stream()
                    .sorted()
                    .collect(Collectors.toUnmodifiableList());
        };
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.CONCURRENT, Characteristics.UNORDERED);
    }

    private static TimeValue getClosest(List<TypedTimeValue> accumulator, TypedTimeValue.Type type, Instant timestamp) {
        return accumulator.stream()
                .filter(e -> e.type().equals(type))
                .filter(e -> e.timestamp().equals(timestamp) || e.timestamp().isBefore(timestamp))
                .sorted()
                .max(comparing(TypedTimeValue::timestamp))
                .map(TypedTimeValue::timeValue)
                .orElse(null);
    }

}
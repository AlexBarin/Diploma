package com.monitoring.threshold.algorithm;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class AutocorrelationAnalyzerTest {

    @Test
    void sineWave_shouldDetectPeriod() {
        int period = 24;
        int dataLength = period * 10;
        double[] data = new double[dataLength];

        for (int i = 0; i < dataLength; i++) {
            data[i] = 100.0 + 30.0 * Math.sin(2.0 * Math.PI * i / period);
        }

        OptionalInt detected = AutocorrelationAnalyzer.detectSeasonLength(data, 2, period * 2);

        assertTrue(detected.isPresent(), "Should detect periodicity in a sine wave");
        assertEquals(period, detected.getAsInt(), 1,
                "Detected period should be close to the true period");
    }

    @Test
    void randomNoise_shouldNotDetectPeriodicity() {
        Random rng = new Random(42);
        int dataLength = 500;
        double[] data = new double[dataLength];

        for (int i = 0; i < dataLength; i++) {
            data[i] = rng.nextGaussian() * 10.0;
        }

        OptionalInt detected = AutocorrelationAnalyzer.detectSeasonLength(data, 2, 100);

        assertFalse(detected.isPresent(),
                "Should not detect periodicity in random noise");
    }

    @Test
    void compositeSignal_sinePlusNoise_shouldStillDetectPeriod() {
        int period = 30;
        int dataLength = period * 12;
        Random rng = new Random(99);
        double[] data = new double[dataLength];

        for (int i = 0; i < dataLength; i++) {
            double signal = 50.0 * Math.sin(2.0 * Math.PI * i / period);
            double noise = rng.nextGaussian() * 10.0;
            data[i] = signal + noise;
        }

        OptionalInt detected = AutocorrelationAnalyzer.detectSeasonLength(data, 2, period * 2);

        assertTrue(detected.isPresent(),
                "Should detect periodicity even with noise added");
        assertEquals(period, detected.getAsInt(), 2,
                "Detected period should be close to the true period despite noise");
    }

    @Test
    void computeACF_constantData_returnsOnesAtLagZero() {
        double[] data = new double[100];
        for (int i = 0; i < 100; i++) {
            data[i] = 42.0;
        }

        double[] acf = AutocorrelationAnalyzer.computeACF(data, 10);

        assertTrue(acf.length > 0, "ACF should have at least one element");
        assertEquals(1.0, acf[0], 0.001, "ACF at lag 0 should be 1.0");
    }

    @Test
    void computeACF_emptyData_returnsEmptyArray() {
        double[] data = new double[0];
        double[] acf = AutocorrelationAnalyzer.computeACF(data, 10);
        assertEquals(0, acf.length, "ACF of empty data should be empty");
    }

    @Test
    void computeACF_zeroMaxLag_returnsEmptyArray() {
        double[] data = {1.0, 2.0, 3.0};
        double[] acf = AutocorrelationAnalyzer.computeACF(data, 0);
        assertEquals(0, acf.length, "ACF with maxLag=0 should be empty");
    }

    @Test
    void computeACF_sineWave_peaksAtPeriod() {
        int period = 20;
        int dataLength = period * 5;
        double[] data = new double[dataLength];

        for (int i = 0; i < dataLength; i++) {
            data[i] = Math.sin(2.0 * Math.PI * i / period);
        }

        double[] acf = AutocorrelationAnalyzer.computeACF(data, period * 2);

        assertTrue(acf[period] > 0.8,
                "ACF at the period lag should be high for a sine wave, got: " + acf[period]);

        assertTrue(acf[period / 2] < -0.5,
                "ACF at half-period should be negative, got: " + acf[period / 2]);
    }

    @Test
    void detectSeasonLength_insufficientData_returnsEmpty() {
        double[] data = new double[10];
        for (int i = 0; i < 10; i++) {
            data[i] = Math.sin(2.0 * Math.PI * i / 5);
        }

        OptionalInt detected = AutocorrelationAnalyzer.detectSeasonLength(data, 2, 10);
        assertFalse(detected.isPresent(),
                "Should return empty when data is too short for the requested max season length");
    }
}

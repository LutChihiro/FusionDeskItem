package com.xfusion.fusiondesk.repository;

import java.time.Instant;

public record EvaluationRunSummary(long id, String promptVersion, String model, int totalCases,
                                   int normalCases, int adversarialCases, double schemaValidRate,
                                   double categoryAccuracy, double priorityAccuracy,
                                   double exactMatchRate, double injectionResistanceRate,
                                   Instant createdAt) { }

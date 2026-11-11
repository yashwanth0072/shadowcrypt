package com.shadowcrypt.ui.analysis;

import com.shadowcrypt.model.AnalysisReport;
import com.shadowcrypt.service.AnalysisService;
import com.shadowcrypt.ui.main.AppState;
import com.shadowcrypt.ui.main.BackgroundTaskRunner;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Runs standalone stego analysis off the FX thread and publishes the report to shared state. */
public final class StegoAnalysisRunner {

    private StegoAnalysisRunner() {
    }

    public static void run(AnalysisService analysisService, Path stegoFile, AppState state,
                           Runnable onComplete, Consumer<Throwable> onFailure) {
        Task<AnalysisReport> task = new Task<>() {
            @Override
            protected AnalysisReport call() {
                return analysisService.analyzeStego(stegoFile);
            }
        };
        BackgroundTaskRunner.run(task,
                report -> {
                    state.lastAnalysisProperty().set(report);
                    complete(onComplete);
                },
                error -> {
                    state.lastAnalysisProperty().set(null);
                    if (onFailure != null) {
                        onFailure.accept(error);
                    }
                    complete(onComplete);
                });
    }

    private static void complete(Runnable onComplete) {
        if (onComplete != null) {
            onComplete.run();
        }
    }
}

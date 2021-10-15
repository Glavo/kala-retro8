package kala.plugins.retro8;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.RandomAccessFile;

public abstract class Retro8ProcessClassesTask extends DefaultTask {
    @OutputFiles
    public abstract Property<FileCollection> getClassFiles();

    @TaskAction
    public void process() {
        getClassFiles().get().forEach(file -> {
            try (RandomAccessFile rf = new RandomAccessFile(file, "rw")) {
                rf.seek(7); // major version
                if (rf.read() > 52) {
                    rf.seek(7);
                    rf.write(52);
                }
            } catch (IOException exception) {
                getLogger().log(LogLevel.WARN, "File: " + file, exception);
            }
        });
    }
}

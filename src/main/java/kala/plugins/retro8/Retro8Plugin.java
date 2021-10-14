package kala.plugins.retro8;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.CodeSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Retro8Plugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Retro8Extension extension = project.getExtensions().create("retro8", Retro8Extension.class);

        project.getTasks().create("retro8ProcessClasses", Retro8ProcessClassesTask.class, task -> {
            task.setGroup("retro8");
            task.getClassFiles().set(project.<FileCollection>provider(() ->
                    project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()
                            .getByName("main")
                            .getOutput()
                            .getClassesDirs()
                            .getFiles()
                            .stream()
                            .map(project::fileTree)
                            .map(tree -> {
                                tree.include("**/*.class");
                                tree.exclude("module-info.class");
                                return (FileTree) tree;
                            })
                            .reduce(FileTree::plus)
                            .orElseGet(() -> project.files().getAsFileTree())
            ));

            task.dependsOn(project.getTasks().getByName("compileJava"));
            project.getTasks().getByName("classes").dependsOn(task);
        });

        File jabelDir = new File(project.getBuildDir(), "jabel");
        project.getTasks().create("retro8ExtractJabel", task -> {
            task.setGroup("retro8");

            Path thisJar = Optional.ofNullable(Retro8Plugin.class.getProtectionDomain().getCodeSource())
                    .map(CodeSource::getLocation)
                    .map(url -> {
                        try {
                            return Paths.get(url.toURI());
                        } catch (Throwable ex) {
                            throw new GradleException("Unable to unzip jabel", ex);
                        }
                    })
                    .orElseThrow(() -> new GradleException("Unable to unzip jabel"));

            try {
                FileSystem fs = FileSystems.newFileSystem(thisJar, (ClassLoader) null);
                Path jabelInJar = fs.getPath("kala/plugins/retro8/jabel");

                List<Path> jabelFiles;
                try (Stream<Path> files = Files.list(jabelInJar)) {
                    jabelFiles = files.collect(Collectors.toList());
                }

                JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
                compileJava.dependsOn(task);

                CompileOptions options = compileJava.getOptions();
                options.getRelease().set(9);

                options.getCompilerArgs().add("-Xplugin:jabel");

                task.doFirst(t -> {
                    ConfigurableFileCollection files =
                            project.files(jabelFiles.stream().map(file -> new File(jabelDir, file.getFileName().toString()))
                                    .collect(Collectors.toList()));

                    options.setAnnotationProcessorPath(
                            Optional.ofNullable(options.getAnnotationProcessorPath())
                                    .map(it -> it.plus(files))
                                    .orElse(files)
                    );

                    try {
                        //noinspection ResultOfMethodCallIgnored
                        jabelDir.mkdirs();

                        for (Path file : jabelFiles) {
                            Path target = new File(jabelDir, file.getFileName().toString()).toPath();
                            if (Files.notExists(target)) {
                                Files.copy(file, target);
                            }
                        }
                    } catch (IOException e) {
                        throw new GradleException("Unable to unzip jabel", e);
                    }
                });
            } catch (IOException e) {
                throw new GradleException("Unable to unzip jabel", e);
            }
        });
    }
}

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.CodeSource;
import java.util.List;
import java.util.Map;
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

        Path jabelDir = Paths.get(System.getProperty("java.io.tmpdir"), "org.glavo.kala-retro8", "jabel");


        project.getTasks().create("retro8ExtractJabel", task -> {
            task.setGroup("retro8");

            try {
                Map<String, Integer> jabelFiles;

                //noinspection ConstantConditions
                try (BufferedReader reader =
                             new BufferedReader(new InputStreamReader(
                                     Retro8Plugin.class.getResourceAsStream("jabel/list.txt"),
                                     StandardCharsets.UTF_8))) {
                    jabelFiles = reader.lines()
                            .filter(s -> !s.isEmpty())
                            .map(s -> s.split(":"))
                            .collect(Collectors.toMap(arr -> arr[0], arr -> Integer.parseInt(arr[1])));
                }

                JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
                compileJava.dependsOn(task);

                CompileOptions options = compileJava.getOptions();
                options.getRelease().set(9);

                options.getCompilerArgs().add("-Xplugin:jabel");

                ConfigurableFileCollection files =
                        project.files(jabelFiles.keySet().stream().map(jabelDir::resolve).collect(Collectors.toList()));

                options.setAnnotationProcessorPath(
                        Optional.ofNullable(options.getAnnotationProcessorPath())
                                .map(it -> it.plus(files))
                                .orElse(files)
                );

                task.doFirst(t -> {
                    try {
                        Files.createDirectories(jabelDir);
                        for (String file : jabelFiles.keySet()) {
                            Path target = jabelDir.resolve(file);
                            if (Files.notExists(target) || Files.size(target) != jabelFiles.get(file)) {
                                project.getLogger().quiet("Extract " + file + " to " + target);
                                try (InputStream source = Retro8Plugin.class.getResourceAsStream("jabel/" + file)) {
                                    //noinspection ConstantConditions
                                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                                }
                                target.toFile().deleteOnExit();
                            }
                        }
                    } catch (Throwable e) {
                        throw new GradleException("Unable to unzip jabel", e);
                    }
                });
            } catch (IOException e) {
                throw new GradleException("Unable to unzip jabel", e);
            }
        });
    }
}

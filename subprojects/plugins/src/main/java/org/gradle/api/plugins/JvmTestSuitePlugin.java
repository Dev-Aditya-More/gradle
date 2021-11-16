/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.TestType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.Verification;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.testing.base.TestSuite;
import org.gradle.testing.base.TestingExtension;

/**
 * A {@link org.gradle.api.Plugin} that adds extensions for declaring, compiling and running {@link JvmTestSuite}s.
 * <p>
 * This plugin provides conventions for several things:
 * <ul>
 *     <li>All other {@code JvmTestSuite} will use the JUnit Jupiter testing framework unless specified otherwise.</li>
 *     <li>A single test suite target is added to each {@code JvmTestSuite}.</li>
 *
 * </ul>
 *
 * @since 7.3
 * @see <a href="https://docs.gradle.org/current/userguide/test_suite_plugin.html">Test Suite plugin reference</a>
 */
@Incubating
public class JvmTestSuitePlugin implements Plugin<Project> {
    public static final String DEFAULT_TEST_SUITE_NAME = SourceSet.TEST_SOURCE_SET_NAME;
    private static final String TEST_RESULTS_ELEMENTS_VARIANT_PREFIX = "testResultsElementsFor";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.test-suite-base");
        project.getPluginManager().apply("org.gradle.java-base");
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();
        testSuites.registerBinding(JvmTestSuite.class, DefaultJvmTestSuite.class);

        // TODO: Deprecate this behavior?
        // Why would any Test task created need to use the test source set's classes?
        project.getTasks().withType(Test.class).configureEach(test -> {
            // The test task may have already been created but the test sourceSet may not exist yet.
            // So defer looking up the java extension and sourceSet until the convention mapping is resolved.
            // See https://github.com/gradle/gradle/issues/18622
            test.getConventionMapping().map("testClassesDirs", () ->  project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().findByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs());
            test.getConventionMapping().map("classpath", () -> project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().findByName(SourceSet.TEST_SOURCE_SET_NAME).getRuntimeClasspath());
            test.getModularity().getInferModulePath().convention(java.getModularity().getInferModulePath());
        });

        testSuites.withType(JvmTestSuite.class).all(testSuite -> {
            testSuite.getTestType().convention(TestType.UNIT_TESTS);
            testSuite.getTargets().all(target -> {
                target.getTestTask().configure(test -> {
                    test.getConventionMapping().map("testClassesDirs", () -> testSuite.getSources().getOutput().getClassesDirs());
                    test.getConventionMapping().map("classpath", () -> testSuite.getSources().getRuntimeClasspath());
                });
            });
        });

        configureTestDataElementsVariants(project);
    }

    private void configureTestDataElementsVariants(Project project) {
        final TestingExtension testing = project.getExtensions().getByType(TestingExtension.class);
        final ExtensiblePolymorphicDomainObjectContainer<TestSuite> testSuites = testing.getSuites();

        testSuites.withType(JvmTestSuite.class).configureEach(suite -> {
            suite.getTargets().configureEach(target -> {
                createTestDataVariant(project, suite, target);
            });
        });
    }

    private Configuration createTestDataVariant(Project project, JvmTestSuite suite, JvmTestSuiteTarget target) {
        final Configuration variant = project.getConfigurations().create(TEST_RESULTS_ELEMENTS_VARIANT_PREFIX + StringUtils.capitalize(target.getName()));
        variant.setVisible(false);
        variant.setCanBeResolved(false);
        variant.setCanBeConsumed(true);
        variant.extendsFrom(project.getConfigurations().getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME));

        final ObjectFactory objects = project.getObjects();
        variant.attributes(attributes -> {
            attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.VERIFICATION));
            attributes.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.class, Category.DOCUMENTATION));
            attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.class, DocsType.TEST_RESULTS));
            attributes.attribute(Verification.TEST_SUITE_NAME_ATTRIBUTE, objects.named(Verification.class, suite.getName()));
            attributes.attribute(Verification.TARGET_NAME_ATTRIBUTE, objects.named(Verification.class, suite.getName()));
            attributes.attribute(TestType.TEST_TYPE_ATTRIBUTE, suite.getTestType().map(tt -> objects.named(TestType.class, tt)));
        });

        variant.getOutgoing().artifact(
            target.getTestTask().flatMap(task -> task.getBinaryResultsDirectory().file("results.bin")),
            artifact -> artifact.setType(ArtifactTypeDefinition.BINARY_DATA_TYPE)
        );

        return variant;
    }
}

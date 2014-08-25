/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ActionBroadcast;
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.TargetedPlatformToolChain;

import java.io.File;

public abstract class ExtendableToolChain<T extends CommandLineToolConfiguration> extends DefaultNamedDomainObjectSet<T> implements ToolChainInternal {
    private final String name;
    protected final OperatingSystem operatingSystem;
    private final FileResolver fileResolver;
    protected final ActionBroadcast<TargetedPlatformToolChain<T>> configureActions = new ActionBroadcast<TargetedPlatformToolChain<T>>();

    protected ExtendableToolChain(Class<? extends T> type, String name, OperatingSystem operatingSystem, FileResolver fileResolver, Instantiator instantiator) {
        super(type, instantiator);
        this.name = name;
        this.operatingSystem = operatingSystem;
        this.fileResolver = fileResolver;
    }

    public String getName() {
        return name;
    }

    protected abstract String getTypeName();

    public String getDisplayName() {
        return String.format("Tool chain '%s' (%s)", getName(), getTypeName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getOutputType() {
        return String.format("%s-%s", getName(), operatingSystem.getName());
    }

    public String getExecutableName(String executablePath) {
        return operatingSystem.getExecutableName(executablePath);
    }

    public String getSharedLibraryName(String libraryName) {
        return operatingSystem.getSharedLibraryName(libraryName);
    }

    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName);
    }

    public String getStaticLibraryName(String libraryName) {
        return operatingSystem.getStaticLibraryName(libraryName);
    }

    public void eachPlatform(Action<? super TargetedPlatformToolChain<T>> action) {
        configureActions.add(action);
    }

    protected File resolve(Object path) {
        return fileResolver.resolve(path);
    }
}

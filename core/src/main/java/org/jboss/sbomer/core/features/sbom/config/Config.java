/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.sbomer.core.features.sbom.config;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.sbomer.core.errors.ApplicationException;
import org.jboss.sbomer.core.features.sbom.utils.ObjectMapperProvider;
import org.jboss.util.Strings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@SuperBuilder(setterPrefix = "with")
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        defaultImpl = PncBuildConfig.class)
@JsonSubTypes({ @JsonSubTypes.Type(SyftImageConfig.class), @JsonSubTypes.Type(PncBuildConfig.class),
        @JsonSubTypes.Type(OperationConfig.class), @JsonSubTypes.Type(DeliverableAnalysisConfig.class),
        @JsonSubTypes.Type(BrewRPMConfig.class) })
public abstract class Config {

    /**
     * The API version of the configuration file. In case of breaking changes this value will be used to detect the
     * correct (de)serializer.
     */
    @Builder.Default
    String apiVersion = "sbomer.jboss.org/v1alpha1";

    /**
     * Checks whether current object is an empty one.
     *
     * @return
     */
    @JsonIgnore
    public abstract boolean isEmpty();

    /**
     * <p>
     * Returns commands to be run to process the manifest.
     * </p>
     *
     * @return List of string that form a command to be run by the CLI.
     */
    @JsonIgnore
    protected List<String> processCommand() {
        return Collections.emptyList();
    }

    /**
     * <p>
     * Returns a command that represents the parameters that should be passed to the process command. This basically
     * translates all the configured processors and it's parameters into a string that can be executed via CLI.
     * </p>
     *
     * <p>
     * By default it returns only {@code default} which represents the default processors that is used in every
     * manifest.
     * </p>
     *
     * <p>
     * Please note that this is not used by all generators! Some generators use similar approach, see
     * {@see ProductConfig#generateCommand(PncBuildConfig))}. This process may be unified at some point.
     * </p>
     *
     * @return A command (parameters) that wil be passed to the standalone process command.
     */
    @JsonIgnore
    public String toProcessorsCommand() {
        List<String> command = processCommand();

        // If there are no processors, make sure the default one is run.
        if (command.isEmpty()) {
            return "default";
        }

        // Convert the list of strings into a single command ensuring that we handle quotes correctly.
        return command.stream().map(param -> {
            if (param.contains(" ")) {
                return "\"" + param + "\"";
            }

            return param;
        }).collect(Collectors.joining(" "));
    }

    public static <T extends Config> T newInstance(Class<T> clazz) {
        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            log.warn("Unable to create a new default config of class '{}'", clazz, e);
            return null;
        }
    }

    public String toJson() {
        try {
            return ObjectMapperProvider.json().writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new ApplicationException("Cannot serialize configuration into a JSON string", e);
        }
    }

    public static Config fromString(String value) {
        return Config.fromString(value, Config.class);
    }

    public static <T extends Config> T fromString(String data, Class<T> clazz) {
        if (data == null || Strings.isEmpty(data)) {
            return null;
        }

        try {
            return ObjectMapperProvider.yaml().readValue(data.getBytes(), clazz);
        } catch (InvalidTypeIdException itide) {
            throw new ApplicationException(
                    "Provided configuration has invalid or missing 'type' identifier: '{}'",
                    data,
                    itide);
        } catch (IOException e) {
            throw new ApplicationException("Cannot deserialize Config: '{}'", data, e);
        }

    }
}

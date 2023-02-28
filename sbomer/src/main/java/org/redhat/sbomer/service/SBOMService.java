/**
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
package org.redhat.sbomer.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.ws.rs.NotFoundException;

import org.redhat.sbomer.model.BaseSBOM;
import org.redhat.sbomer.repositories.BaseSBOMRepository;
import org.redhat.sbomer.service.generator.SBOMGenerator;
import org.redhat.sbomer.validation.exceptions.ValidationException;
import org.jboss.pnc.common.concurrent.Sequence;
import org.redhat.sbomer.dto.response.Page;
import org.redhat.sbomer.mappers.api.BaseSBOMMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Main SBOM service that is dealing with the {@link BaseSBOM} resource.
 */
@ApplicationScoped
@Slf4j
public class SBOMService {

    @Inject
    BaseSBOMRepository repository;

    @Inject
    PNCService pncService;

    @Inject
    SBOMGenerator sbomGenerator;

    @Inject
    BaseSBOMMapper baseSBOMMapper;

    @Inject
    Validator validator;

    /**
     * Runs the generation of SBOM using the available implementation of the generator. This is done in an asynchronous
     * way -- the generation is run behind the scenes.
     *
     * @param buildId
     */
    public void createBomFromPncBuild(String buildId) {
        sbomGenerator.generate(buildId);
    }

    public Page<org.redhat.sbomer.dto.BaseSBOM> listBaseSboms(int pageIndex, int pageSize) {
        log.debug("Getting list of all base SBOMS with pageIndex: {}, pageSize: {}", pageIndex, pageSize);

        List<BaseSBOM> collection = repository.findAll().page(pageIndex, pageSize).list();
        int totalPages = repository.findAll().page(io.quarkus.panache.common.Page.ofSize(pageSize)).pageCount();
        long totalHits = repository.findAll().count();
        List<org.redhat.sbomer.dto.BaseSBOM> content = nullableStreamOf(collection).map(baseSBOMMapper::toDTO)
                .collect(Collectors.toList());

        return new Page<org.redhat.sbomer.dto.BaseSBOM>(pageIndex, pageSize, totalPages, totalHits, content);
    }

    public org.redhat.sbomer.dto.BaseSBOM getBaseSbom(String buildId) {
        log.debug("Getting base SBOMS with buildId: {}", buildId);
        try {
            BaseSBOM dbEntity = repository.getBaseSbom(buildId);
            return baseSBOMMapper.toDTO(dbEntity);
        } catch (NoResultException nre) {
            throw new NotFoundException("Base SBOM for build id " + buildId + " not found.");
        }
    }

    /**
     * Persist changes to the {@link BaseSBOM} in the database.
     *
     * @param baseSbom
     * @return
     */
    @Transactional
    public org.redhat.sbomer.dto.BaseSBOM saveBom(org.redhat.sbomer.dto.BaseSBOM baseSbom) throws ValidationException {
        log.debug("Storing entity: " + baseSbom.toString());
        BaseSBOM dbEntity = baseSBOMMapper.toEntity(baseSbom);

        Set<ConstraintViolation<BaseSBOM>> violations = validator.validate(dbEntity);
        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        dbEntity.setGenerationTime(Instant.now());
        dbEntity.setId(Sequence.nextId());
        repository.persistAndFlush(dbEntity);
        return baseSBOMMapper.toDTO(dbEntity);
    }

    public static <T> Stream<T> nullableStreamOf(Collection<T> nullableCollection) {
        if (nullableCollection == null) {
            return Stream.empty();
        }
        return nullableCollection.stream();
    }
}

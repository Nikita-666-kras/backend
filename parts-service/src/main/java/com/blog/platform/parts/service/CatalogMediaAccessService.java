package com.blog.platform.parts.service;

import com.blog.platform.parts.repository.DroneRepository;
import com.blog.platform.parts.repository.KitRepository;
import com.blog.platform.parts.repository.PartRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogMediaAccessService {

    private final PartRepository partRepository;
    private final KitRepository kitRepository;
    private final DroneRepository droneRepository;

    @Transactional(readOnly = true)
    public boolean isReferencedByPublished(UUID mediaId) {
        if (mediaId == null) {
            return false;
        }
        return partRepository.existsPublishedMediaReference(mediaId)
                || kitRepository.existsPublishedMediaReference(mediaId)
                || droneRepository.existsPublishedMediaReference(mediaId);
    }
}

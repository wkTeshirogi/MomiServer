package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.imports;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Repository内部で利用する取込行表現。 */
record VehicleImportRow(UUID id, String displayFilename, String mediaType, String storageReference,
		VehicleImportStatus status, VehicleImportCandidate candidate, String extractionMethod,
		List<String> warnings, LocalDateTime expiresAt, String createdBy, Long confirmedVehicleId) {
}

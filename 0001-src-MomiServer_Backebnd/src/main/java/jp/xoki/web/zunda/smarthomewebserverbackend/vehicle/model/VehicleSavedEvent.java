package jp.xoki.web.zunda.smarthomewebserverbackend.vehicle.model;

/** 保存確定後に期限判定する車両の識別子。原本や所有者情報は含めない。 */
public record VehicleSavedEvent(Long vehicleId) { }

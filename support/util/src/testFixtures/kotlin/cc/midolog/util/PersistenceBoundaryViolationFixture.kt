package cc.midolog.util

import cc.midolog.storage.jpa.config.JpaStorageConfig

/**
 * 가드 동작 입증용 임시 위반 픽스처.
 * PersistenceBoundaryTest에서 금지된 import가 감지되어 빨간색(실패)이 되는지 확인하기 위한 용도.
 */
class PersistenceBoundaryViolationFixture

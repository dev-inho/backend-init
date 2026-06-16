package cc.midolog.sample.port.file

/** 파일 저장 출력 포트. client 어댑터가 구현한다. */
interface FileStoragePort {
    suspend fun store(path: String, bytes: ByteArray): String
}

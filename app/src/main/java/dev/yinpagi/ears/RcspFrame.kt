package dev.yinpagi.ears

object RcspFrame {
    const val SYNC0: Byte = 0xFE.toByte()
    const val SYNC1: Byte = 0xDC.toByte()
    const val CMD_FUNCTION: Int = 0x01
    const val CMD_GET_SYS_INFO: Int = 0x14
    const val CMD_SET_SYS_INFO: Int = 0x15
    const val SYS_INFO_ATTR_CURRENT_NOISE_MODE: Int = 0x41
    const val ANC_MODE_OFF: Byte = 0x00
    const val ANC_MODE_ON: Byte = 0x01
    const val ANC_MODE_TRANSPARENCY: Byte = 0x02

    private var seq: Int = 0

    fun build(opCode: Int, params: ByteArray = byteArrayOf()): ByteArray {
        val payload = ByteArray(3 + params.size)
        payload[0] = (opCode shr 8).toByte()
        payload[1] = opCode.toByte()
        payload[2] = 0
        System.arraycopy(params, 0, payload, 3, params.size)
        val len = payload.size
        val body = ByteArray(4 + len)
        body[0] = SYNC0
        body[1] = SYNC1
        body[2] = (len shr 8).toByte()
        body[3] = len.toByte()
        body[4] = (seq++ and 0xFF).toByte()
        body[5] = 0
        System.arraycopy(payload, 0, body, 6, len)
        val crc = crc16(body, 2, body.size - 2 - 2)
        body[body.size - 2] = (crc shr 8).toByte()
        body[body.size - 1] = crc.toByte()
        return body
    }

    fun setNoiseMode(mode: Byte): ByteArray =
        build(CMD_SET_SYS_INFO, byteArrayOf(SYS_INFO_ATTR_CURRENT_NOISE_MODE.toByte(), mode))

    fun queryNoiseMode(): ByteArray =
        build(CMD_GET_SYS_INFO, byteArrayOf(SYS_INFO_ATTR_CURRENT_NOISE_MODE.toByte()))

    private fun crc16(data: ByteArray, off: Int, len: Int): Int {
        var crc = 0
        for (i in off until off + len) {
            crc = crc xor (data[i].toInt() and 0xFF shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    fun parse(data: ByteArray): ByteArray? {
        if (data.size < 8) return null
        if (data[0] != SYNC0 || data[1] != SYNC1) return null
        val len = ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        if (6 + len > data.size) return null
        val payload = ByteArray(len)
        System.arraycopy(data, 6, payload, 0, len)
        return payload
    }
}

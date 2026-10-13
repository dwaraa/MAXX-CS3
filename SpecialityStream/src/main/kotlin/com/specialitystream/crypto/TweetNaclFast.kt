package com.specialitystream.crypto

object TweetNaclFast {

    class SecretBox(private val key: ByteArray) {
        companion object {
            const val keyLength = 32
            const val nonceLength = 24
            const val overheadLength = 16
            private const val zerobytesLength = 32
            private const val boxzerobytesLength = 16
        }

        fun box(message: ByteArray, theNonce: ByteArray): ByteArray {
            val m = ByteArray(zerobytesLength + message.size)
            System.arraycopy(message, 0, m, zerobytesLength, message.size)
            val c = ByteArray(m.size)
            if (cryptoSecretbox(c, m, m.size, theNonce, key) != 0) {
                error("SecretBox failed")
            }
            val ret = ByteArray(c.size - boxzerobytesLength)
            System.arraycopy(c, boxzerobytesLength, ret, 0, ret.size)
            return ret
        }
    }

    private val sigma = byteArrayOf(101, 120, 112, 97, 110, 100, 32, 51, 50, 45, 98, 121, 116, 101, 32, 107)

    private fun cryptoSecretbox(c: ByteArray, m: ByteArray, d: Int, n: ByteArray, k: ByteArray): Int {
        if (d < 32) return -1
        cryptoStreamXor(c, 0, m, 0, d.toLong(), n, k)
        cryptoOnetimeauth(c, 16, c, 32, d - 32, c)
        return 0
    }

    private fun cryptoStreamXor(c: ByteArray, cpos: Int, m: ByteArray, mpos: Int, d: Long, n: ByteArray, k: ByteArray): Int {
        val s = ByteArray(32)
        coreHsalsa20(s, n, k, sigma)
        val sn = ByteArray(8)
        for (i in 0 until 8) sn[i] = n[i + 16]
        return cryptoStreamSalsa20Xor(c, cpos, m, mpos, d, sn, s)
    }

    private fun cryptoStreamSalsa20Xor(
        c: ByteArray,
        cposIn: Int,
        m: ByteArray,
        mposIn: Int,
        bIn: Long,
        n: ByteArray,
        k: ByteArray
    ): Int {
        var cpos = cposIn
        var mpos = mposIn
        var b = bIn

        val z = ByteArray(16)
        val x = ByteArray(64)
        var u: Int
        for (i in 0 until 8) z[i] = n[i]

        while (b >= 64) {
            cryptoCoreSalsa20(x, z, k, sigma)
            for (i in 0 until 64) c[cpos + i] = ((m[mpos + i].toInt() xor x[i].toInt()) and 0xff).toByte()
            u = 1
            for (i in 8 until 16) {
                u += (z[i].toInt() and 0xff)
                z[i] = (u and 0xff).toByte()
                u = u ushr 8
            }
            b -= 64
            cpos += 64
            mpos += 64
        }
        if (b > 0) {
            cryptoCoreSalsa20(x, z, k, sigma)
            for (i in 0 until b.toInt()) c[cpos + i] = ((m[mpos + i].toInt() xor x[i].toInt()) and 0xff).toByte()
        }
        return 0
    }

    private fun coreHsalsa20(o: ByteArray, p: ByteArray, k: ByteArray, c: ByteArray) {
        val j0 = (c[0].toInt() and 0xff) or ((c[1].toInt() and 0xff) shl 8) or ((c[2].toInt() and 0xff) shl 16) or ((c[3].toInt() and 0xff) shl 24)
        val j1 = (k[0].toInt() and 0xff) or ((k[1].toInt() and 0xff) shl 8) or ((k[2].toInt() and 0xff) shl 16) or ((k[3].toInt() and 0xff) shl 24)
        val j2 = (k[4].toInt() and 0xff) or ((k[5].toInt() and 0xff) shl 8) or ((k[6].toInt() and 0xff) shl 16) or ((k[7].toInt() and 0xff) shl 24)
        val j3 = (k[8].toInt() and 0xff) or ((k[9].toInt() and 0xff) shl 8) or ((k[10].toInt() and 0xff) shl 16) or ((k[11].toInt() and 0xff) shl 24)
        val j4 = (k[12].toInt() and 0xff) or ((k[13].toInt() and 0xff) shl 8) or ((k[14].toInt() and 0xff) shl 16) or ((k[15].toInt() and 0xff) shl 24)
        val j5 = (c[4].toInt() and 0xff) or ((c[5].toInt() and 0xff) shl 8) or ((c[6].toInt() and 0xff) shl 16) or ((c[7].toInt() and 0xff) shl 24)
        val j6 = (p[0].toInt() and 0xff) or ((p[1].toInt() and 0xff) shl 8) or ((p[2].toInt() and 0xff) shl 16) or ((p[3].toInt() and 0xff) shl 24)
        val j7 = (p[4].toInt() and 0xff) or ((p[5].toInt() and 0xff) shl 8) or ((p[6].toInt() and 0xff) shl 16) or ((p[7].toInt() and 0xff) shl 24)
        val j8 = (p[8].toInt() and 0xff) or ((p[9].toInt() and 0xff) shl 8) or ((p[10].toInt() and 0xff) shl 16) or ((p[11].toInt() and 0xff) shl 24)
        val j9 = (p[12].toInt() and 0xff) or ((p[13].toInt() and 0xff) shl 8) or ((p[14].toInt() and 0xff) shl 16) or ((p[15].toInt() and 0xff) shl 24)
        val j10 = (c[8].toInt() and 0xff) or ((c[9].toInt() and 0xff) shl 8) or ((c[10].toInt() and 0xff) shl 16) or ((c[11].toInt() and 0xff) shl 24)
        val j11 = (k[16].toInt() and 0xff) or ((k[17].toInt() and 0xff) shl 8) or ((k[18].toInt() and 0xff) shl 16) or ((k[19].toInt() and 0xff) shl 24)
        val j12 = (k[20].toInt() and 0xff) or ((k[21].toInt() and 0xff) shl 8) or ((k[22].toInt() and 0xff) shl 16) or ((k[23].toInt() and 0xff) shl 24)
        val j13 = (k[24].toInt() and 0xff) or ((k[25].toInt() and 0xff) shl 8) or ((k[26].toInt() and 0xff) shl 16) or ((k[27].toInt() and 0xff) shl 24)
        val j14 = (k[28].toInt() and 0xff) or ((k[29].toInt() and 0xff) shl 8) or ((k[30].toInt() and 0xff) shl 16) or ((k[31].toInt() and 0xff) shl 24)
        val j15 = (c[12].toInt() and 0xff) or ((c[13].toInt() and 0xff) shl 8) or ((c[14].toInt() and 0xff) shl 16) or ((c[15].toInt() and 0xff) shl 24)

        var x0 = j0; var x1 = j1; var x2 = j2; var x3 = j3
        var x4 = j4; var x5 = j5; var x6 = j6; var x7 = j7
        var x8 = j8; var x9 = j9; var x10 = j10; var x11 = j11
        var x12 = j12; var x13 = j13; var x14 = j14; var x15 = j15
        var u: Int

        for (i in 0 until 20 step 2) {
            u = x0 + x12; x4 = x4 xor ((u shl 7) or (u ushr 25))
            u = x4 + x0;  x8 = x8 xor ((u shl 9) or (u ushr 23))
            u = x8 + x4;  x12 = x12 xor ((u shl 13) or (u ushr 19))
            u = x12 + x8; x0 = x0 xor ((u shl 18) or (u ushr 14))

            u = x5 + x1;  x9 = x9 xor ((u shl 7) or (u ushr 25))
            u = x9 + x5;  x13 = x13 xor ((u shl 9) or (u ushr 23))
            u = x13 + x9; x1 = x1 xor ((u shl 13) or (u ushr 19))
            u = x1 + x13; x5 = x5 xor ((u shl 18) or (u ushr 14))

            u = x10 + x6;  x14 = x14 xor ((u shl 7) or (u ushr 25))
            u = x14 + x10; x2 = x2 xor ((u shl 9) or (u ushr 23))
            u = x2 + x14;  x6 = x6 xor ((u shl 13) or (u ushr 19))
            u = x6 + x2;   x10 = x10 xor ((u shl 18) or (u ushr 14))

            u = x15 + x11; x3 = x3 xor ((u shl 7) or (u ushr 25))
            u = x3 + x15;  x7 = x7 xor ((u shl 9) or (u ushr 23))
            u = x7 + x3;   x11 = x11 xor ((u shl 13) or (u ushr 19))
            u = x11 + x7;  x15 = x15 xor ((u shl 18) or (u ushr 14))

            u = x0 + x3;  x1 = x1 xor ((u shl 7) or (u ushr 25))
            u = x1 + x0;  x2 = x2 xor ((u shl 9) or (u ushr 23))
            u = x2 + x1;  x3 = x3 xor ((u shl 13) or (u ushr 19))
            u = x3 + x2;  x0 = x0 xor ((u shl 18) or (u ushr 14))

            u = x5 + x4;  x6 = x6 xor ((u shl 7) or (u ushr 25))
            u = x6 + x5;  x7 = x7 xor ((u shl 9) or (u ushr 23))
            u = x7 + x6;  x4 = x4 xor ((u shl 13) or (u ushr 19))
            u = x4 + x7;  x5 = x5 xor ((u shl 18) or (u ushr 14))

            u = x10 + x9;  x11 = x11 xor ((u shl 7) or (u ushr 25))
            u = x11 + x10; x8 = x8 xor ((u shl 9) or (u ushr 23))
            u = x8 + x11;  x9 = x9 xor ((u shl 13) or (u ushr 19))
            u = x9 + x8;   x10 = x10 xor ((u shl 18) or (u ushr 14))

            u = x15 + x14; x12 = x12 xor ((u shl 7) or (u ushr 25))
            u = x12 + x15; x13 = x13 xor ((u shl 9) or (u ushr 23))
            u = x13 + x12; x14 = x14 xor ((u shl 13) or (u ushr 19))
            u = x14 + x13; x15 = x15 xor ((u shl 18) or (u ushr 14))
        }

        o[0] = (x0 ushr 0).toByte();   o[1] = (x0 ushr 8).toByte();   o[2] = (x0 ushr 16).toByte();  o[3] = (x0 ushr 24).toByte()
        o[4] = (x5 ushr 0).toByte();   o[5] = (x5 ushr 8).toByte();   o[6] = (x5 ushr 16).toByte();  o[7] = (x5 ushr 24).toByte()
        o[8] = (x10 ushr 0).toByte();  o[9] = (x10 ushr 8).toByte();  o[10] = (x10 ushr 16).toByte(); o[11] = (x10 ushr 24).toByte()
        o[12] = (x15 ushr 0).toByte(); o[13] = (x15 ushr 8).toByte(); o[14] = (x15 ushr 16).toByte(); o[15] = (x15 ushr 24).toByte()
        o[16] = (x6 ushr 0).toByte();  o[17] = (x6 ushr 8).toByte();  o[18] = (x6 ushr 16).toByte();  o[19] = (x6 ushr 24).toByte()
        o[20] = (x7 ushr 0).toByte();  o[21] = (x7 ushr 8).toByte();  o[22] = (x7 ushr 16).toByte();  o[23] = (x7 ushr 24).toByte()
        o[24] = (x8 ushr 0).toByte();  o[25] = (x8 ushr 8).toByte();  o[26] = (x8 ushr 16).toByte();  o[27] = (x8 ushr 24).toByte()
        o[28] = (x9 ushr 0).toByte();  o[29] = (x9 ushr 8).toByte();  o[30] = (x9 ushr 16).toByte();  o[31] = (x9 ushr 24).toByte()
    }

    private fun cryptoCoreSalsa20(o: ByteArray, p: ByteArray, k: ByteArray, c: ByteArray) {
        val j0 = (c[0].toInt() and 0xff) or ((c[1].toInt() and 0xff) shl 8) or ((c[2].toInt() and 0xff) shl 16) or ((c[3].toInt() and 0xff) shl 24)
        val j1 = (k[0].toInt() and 0xff) or ((k[1].toInt() and 0xff) shl 8) or ((k[2].toInt() and 0xff) shl 16) or ((k[3].toInt() and 0xff) shl 24)
        val j2 = (k[4].toInt() and 0xff) or ((k[5].toInt() and 0xff) shl 8) or ((k[6].toInt() and 0xff) shl 16) or ((k[7].toInt() and 0xff) shl 24)
        val j3 = (k[8].toInt() and 0xff) or ((k[9].toInt() and 0xff) shl 8) or ((k[10].toInt() and 0xff) shl 16) or ((k[11].toInt() and 0xff) shl 24)
        val j4 = (k[12].toInt() and 0xff) or ((k[13].toInt() and 0xff) shl 8) or ((k[14].toInt() and 0xff) shl 16) or ((k[15].toInt() and 0xff) shl 24)
        val j5 = (c[4].toInt() and 0xff) or ((c[5].toInt() and 0xff) shl 8) or ((c[6].toInt() and 0xff) shl 16) or ((c[7].toInt() and 0xff) shl 24)
        val j6 = (p[0].toInt() and 0xff) or ((p[1].toInt() and 0xff) shl 8) or ((p[2].toInt() and 0xff) shl 16) or ((p[3].toInt() and 0xff) shl 24)
        val j7 = (p[4].toInt() and 0xff) or ((p[5].toInt() and 0xff) shl 8) or ((p[6].toInt() and 0xff) shl 16) or ((p[7].toInt() and 0xff) shl 24)
        val j8 = (p[8].toInt() and 0xff) or ((p[9].toInt() and 0xff) shl 8) or ((p[10].toInt() and 0xff) shl 16) or ((p[11].toInt() and 0xff) shl 24)
        val j9 = (p[12].toInt() and 0xff) or ((p[13].toInt() and 0xff) shl 8) or ((p[14].toInt() and 0xff) shl 16) or ((p[15].toInt() and 0xff) shl 24)
        val j10 = (c[8].toInt() and 0xff) or ((c[9].toInt() and 0xff) shl 8) or ((c[10].toInt() and 0xff) shl 16) or ((c[11].toInt() and 0xff) shl 24)
        val j11 = (k[16].toInt() and 0xff) or ((k[17].toInt() and 0xff) shl 8) or ((k[18].toInt() and 0xff) shl 16) or ((k[19].toInt() and 0xff) shl 24)
        val j12 = (k[20].toInt() and 0xff) or ((k[21].toInt() and 0xff) shl 8) or ((k[22].toInt() and 0xff) shl 16) or ((k[23].toInt() and 0xff) shl 24)
        val j13 = (k[24].toInt() and 0xff) or ((k[25].toInt() and 0xff) shl 8) or ((k[26].toInt() and 0xff) shl 16) or ((k[27].toInt() and 0xff) shl 24)
        val j14 = (k[28].toInt() and 0xff) or ((k[29].toInt() and 0xff) shl 8) or ((k[30].toInt() and 0xff) shl 16) or ((k[31].toInt() and 0xff) shl 24)
        val j15 = (c[12].toInt() and 0xff) or ((c[13].toInt() and 0xff) shl 8) or ((c[14].toInt() and 0xff) shl 16) or ((c[15].toInt() and 0xff) shl 24)

        var x0 = j0; var x1 = j1; var x2 = j2; var x3 = j3
        var x4 = j4; var x5 = j5; var x6 = j6; var x7 = j7
        var x8 = j8; var x9 = j9; var x10 = j10; var x11 = j11
        var x12 = j12; var x13 = j13; var x14 = j14; var x15 = j15
        var u: Int

        for (i in 0 until 20 step 2) {
            u = x0 + x12; x4 = x4 xor ((u shl 7) or (u ushr 25))
            u = x4 + x0;  x8 = x8 xor ((u shl 9) or (u ushr 23))
            u = x8 + x4;  x12 = x12 xor ((u shl 13) or (u ushr 19))
            u = x12 + x8; x0 = x0 xor ((u shl 18) or (u ushr 14))

            u = x5 + x1;  x9 = x9 xor ((u shl 7) or (u ushr 25))
            u = x9 + x5;  x13 = x13 xor ((u shl 9) or (u ushr 23))
            u = x13 + x9; x1 = x1 xor ((u shl 13) or (u ushr 19))
            u = x1 + x13; x5 = x5 xor ((u shl 18) or (u ushr 14))

            u = x10 + x6;  x14 = x14 xor ((u shl 7) or (u ushr 25))
            u = x14 + x10; x2 = x2 xor ((u shl 9) or (u ushr 23))
            u = x2 + x14;  x6 = x6 xor ((u shl 13) or (u ushr 19))
            u = x6 + x2;   x10 = x10 xor ((u shl 18) or (u ushr 14))

            u = x15 + x11; x3 = x3 xor ((u shl 7) or (u ushr 25))
            u = x3 + x15;  x7 = x7 xor ((u shl 9) or (u ushr 23))
            u = x7 + x3;   x11 = x11 xor ((u shl 13) or (u ushr 19))
            u = x11 + x7;  x15 = x15 xor ((u shl 18) or (u ushr 14))

            u = x0 + x3;  x1 = x1 xor ((u shl 7) or (u ushr 25))
            u = x1 + x0;  x2 = x2 xor ((u shl 9) or (u ushr 23))
            u = x2 + x1;  x3 = x3 xor ((u shl 13) or (u ushr 19))
            u = x3 + x2;  x0 = x0 xor ((u shl 18) or (u ushr 14))

            u = x5 + x4;  x6 = x6 xor ((u shl 7) or (u ushr 25))
            u = x6 + x5;  x7 = x7 xor ((u shl 9) or (u ushr 23))
            u = x7 + x6;  x4 = x4 xor ((u shl 13) or (u ushr 19))
            u = x4 + x7;  x5 = x5 xor ((u shl 18) or (u ushr 14))

            u = x10 + x9;  x11 = x11 xor ((u shl 7) or (u ushr 25))
            u = x11 + x10; x8 = x8 xor ((u shl 9) or (u ushr 23))
            u = x8 + x11;  x9 = x9 xor ((u shl 13) or (u ushr 19))
            u = x9 + x8;   x10 = x10 xor ((u shl 18) or (u ushr 14))

            u = x15 + x14; x12 = x12 xor ((u shl 7) or (u ushr 25))
            u = x12 + x15; x13 = x13 xor ((u shl 9) or (u ushr 23))
            u = x13 + x12; x14 = x14 xor ((u shl 13) or (u ushr 19))
            u = x14 + x13; x15 = x15 xor ((u shl 18) or (u ushr 14))
        }

        x0 += j0; x1 += j1; x2 += j2; x3 += j3
        x4 += j4; x5 += j5; x6 += j6; x7 += j7
        x8 += j8; x9 += j9; x10 += j10; x11 += j11
        x12 += j12; x13 += j13; x14 += j14; x15 += j15

        o[0] = (x0 ushr 0).toByte();   o[1] = (x0 ushr 8).toByte();   o[2] = (x0 ushr 16).toByte();  o[3] = (x0 ushr 24).toByte()
        o[4] = (x1 ushr 0).toByte();   o[5] = (x1 ushr 8).toByte();   o[6] = (x1 ushr 16).toByte();  o[7] = (x1 ushr 24).toByte()
        o[8] = (x2 ushr 0).toByte();   o[9] = (x2 ushr 8).toByte();   o[10] = (x2 ushr 16).toByte(); o[11] = (x2 ushr 24).toByte()
        o[12] = (x3 ushr 0).toByte();  o[13] = (x3 ushr 8).toByte();  o[14] = (x3 ushr 16).toByte(); o[15] = (x3 ushr 24).toByte()
        o[16] = (x4 ushr 0).toByte();  o[17] = (x4 ushr 8).toByte();  o[18] = (x4 ushr 16).toByte(); o[19] = (x4 ushr 24).toByte()
        o[20] = (x5 ushr 0).toByte();  o[21] = (x5 ushr 8).toByte();  o[22] = (x5 ushr 16).toByte(); o[23] = (x5 ushr 24).toByte()
        o[24] = (x6 ushr 0).toByte();  o[25] = (x6 ushr 8).toByte();  o[26] = (x6 ushr 16).toByte(); o[27] = (x6 ushr 24).toByte()
        o[28] = (x7 ushr 0).toByte();  o[29] = (x7 ushr 8).toByte();  o[30] = (x7 ushr 16).toByte(); o[31] = (x7 ushr 24).toByte()
        o[32] = (x8 ushr 0).toByte();  o[33] = (x8 ushr 8).toByte();  o[34] = (x8 ushr 16).toByte(); o[35] = (x8 ushr 24).toByte()
        o[36] = (x9 ushr 0).toByte();  o[37] = (x9 ushr 8).toByte();  o[38] = (x9 ushr 16).toByte(); o[39] = (x9 ushr 24).toByte()
        o[40] = (x10 ushr 0).toByte(); o[41] = (x10 ushr 8).toByte(); o[42] = (x10 ushr 16).toByte(); o[43] = (x10 ushr 24).toByte()
        o[44] = (x11 ushr 0).toByte(); o[45] = (x11 ushr 8).toByte(); o[46] = (x11 ushr 16).toByte(); o[47] = (x11 ushr 24).toByte()
        o[48] = (x12 ushr 0).toByte(); o[49] = (x12 ushr 8).toByte(); o[50] = (x12 ushr 16).toByte(); o[51] = (x12 ushr 24).toByte()
        o[52] = (x13 ushr 0).toByte(); o[53] = (x13 ushr 8).toByte(); o[54] = (x13 ushr 16).toByte(); o[55] = (x13 ushr 24).toByte()
        o[56] = (x14 ushr 0).toByte(); o[57] = (x14 ushr 8).toByte(); o[58] = (x14 ushr 16).toByte(); o[59] = (x14 ushr 24).toByte()
        o[60] = (x15 ushr 0).toByte(); o[61] = (x15 ushr 8).toByte(); o[62] = (x15 ushr 16).toByte(); o[63] = (x15 ushr 24).toByte()
    }

    private fun cryptoOnetimeauth(out: ByteArray, outpos: Int, m: ByteArray, mpos: Int, n: Int, k: ByteArray): Int {
        val s = Poly1305(k)
        s.update(m, mpos, n)
        s.finish(out, outpos)
        return 0
    }

    private class Poly1305(key: ByteArray) {
        private val r = LongArray(10)
        private val h = LongArray(10)
        private val pad = LongArray(8)
        private val buffer = ByteArray(16)
        private var leftover = 0
        private var fin = 0

        init {
            val t0 = (key[0].toLong() and 0xffL) or ((key[1].toLong() and 0xffL) shl 8)
            val t1 = (key[2].toLong() and 0xffL) or ((key[3].toLong() and 0xffL) shl 8)
            val t2 = (key[4].toLong() and 0xffL) or ((key[5].toLong() and 0xffL) shl 8)
            val t3 = (key[6].toLong() and 0xffL) or ((key[7].toLong() and 0xffL) shl 8)
            val t4 = (key[8].toLong() and 0xffL) or ((key[9].toLong() and 0xffL) shl 8)
            val t5 = (key[10].toLong() and 0xffL) or ((key[11].toLong() and 0xffL) shl 8)
            val t6 = (key[12].toLong() and 0xffL) or ((key[13].toLong() and 0xffL) shl 8)
            val t7 = (key[14].toLong() and 0xffL) or ((key[15].toLong() and 0xffL) shl 8)

            r[0] = t0 and 0x1fffL
            r[1] = ((t0 ushr 13) or (t1 shl 3)) and 0x1fffL
            r[2] = ((t1 ushr 10) or (t2 shl 6)) and 0x1f03L
            r[3] = ((t2 ushr 7) or (t3 shl 9)) and 0x1fffL
            r[4] = ((t3 ushr 4) or (t4 shl 12)) and 0x00ffL
            r[5] = (t4 ushr 1) and 0x1ffeL
            r[6] = ((t4 ushr 14) or (t5 shl 2)) and 0x1fffL
            r[7] = ((t5 ushr 11) or (t6 shl 5)) and 0x1f81L
            r[8] = ((t6 ushr 8) or (t7 shl 8)) and 0x1fffL
            r[9] = (t7 ushr 5) and 0x007fL

            for (i in 0 until 8) {
                pad[i] = (key[16 + (2 * i)].toLong() and 0xffL) or ((key[17 + (2 * i)].toLong() and 0xffL) shl 8)
            }
        }

        fun update(m: ByteArray, offset: Int, bytes: Int) {
            var mpos = offset
            var remaining = bytes

            if (leftover > 0) {
                var want = 16 - leftover
                if (want > remaining) want = remaining
                for (i in 0 until want) buffer[leftover + i] = m[mpos + i]
                remaining -= want
                mpos += want
                leftover += want
                if (leftover < 16) return
                blocks(buffer, 0, 16)
                leftover = 0
            }

            if (remaining >= 16) {
                val want = remaining and 15.inv()
                blocks(m, mpos, want)
                mpos += want
                remaining -= want
            }

            if (remaining > 0) {
                for (i in 0 until remaining) buffer[leftover + i] = m[mpos + i]
                leftover += remaining
            }
        }

        private fun blocks(m: ByteArray, offset: Int, bytes: Int) {
            val hibit = if (fin != 0) 0L else (1L shl 11)
            var mpos = offset
            var rem = bytes

            while (rem >= 16) {
                val t0 = (m[mpos + 0].toLong() and 0xffL) or ((m[mpos + 1].toLong() and 0xffL) shl 8)
                val t1 = (m[mpos + 2].toLong() and 0xffL) or ((m[mpos + 3].toLong() and 0xffL) shl 8)
                val t2 = (m[mpos + 4].toLong() and 0xffL) or ((m[mpos + 5].toLong() and 0xffL) shl 8)
                val t3 = (m[mpos + 6].toLong() and 0xffL) or ((m[mpos + 7].toLong() and 0xffL) shl 8)
                val t4 = (m[mpos + 8].toLong() and 0xffL) or ((m[mpos + 9].toLong() and 0xffL) shl 8)
                val t5 = (m[mpos + 10].toLong() and 0xffL) or ((m[mpos + 11].toLong() and 0xffL) shl 8)
                val t6 = (m[mpos + 12].toLong() and 0xffL) or ((m[mpos + 13].toLong() and 0xffL) shl 8)
                val t7 = (m[mpos + 14].toLong() and 0xffL) or ((m[mpos + 15].toLong() and 0xffL) shl 8)

                h[0] += (t0 and 0x1fffL)
                h[1] += (((t0 ushr 13) or (t1 shl 3)) and 0x1fffL)
                h[2] += (((t1 ushr 10) or (t2 shl 6)) and 0x1fffL)
                h[3] += (((t2 ushr 7) or (t3 shl 9)) and 0x1fffL)
                h[4] += (((t3 ushr 4) or (t4 shl 12)) and 0x1fffL)
                h[5] += ((t4 ushr 1) and 0x1fffL)
                h[6] += (((t4 ushr 14) or (t5 shl 2)) and 0x1fffL)
                h[7] += (((t5 ushr 11) or (t6 shl 5)) and 0x1fffL)
                h[8] += (((t6 ushr 8) or (t7 shl 8)) and 0x1fffL)
                h[9] += ((t7 ushr 5) or hibit)

                var c: Long
                val d0 = h[0] * r[0] + h[1] * (5L * r[9]) + h[2] * (5L * r[8]) + h[3] * (5L * r[7]) + h[4] * (5L * r[6]) + h[5] * (5L * r[5]) + h[6] * (5L * r[4]) + h[7] * (5L * r[3]) + h[8] * (5L * r[2]) + h[9] * (5L * r[1]); c = d0 ushr 13
                val d1 = c + h[0] * r[1] + h[1] * r[0] + h[2] * (5L * r[9]) + h[3] * (5L * r[8]) + h[4] * (5L * r[7]) + h[5] * (5L * r[6]) + h[6] * (5L * r[5]) + h[7] * (5L * r[4]) + h[8] * (5L * r[3]) + h[9] * (5L * r[2]); c = d1 ushr 13
                val d2 = c + h[0] * r[2] + h[1] * r[1] + h[2] * r[0] + h[3] * (5L * r[9]) + h[4] * (5L * r[8]) + h[5] * (5L * r[7]) + h[6] * (5L * r[6]) + h[7] * (5L * r[5]) + h[8] * (5L * r[4]) + h[9] * (5L * r[3]); c = d2 ushr 13
                val d3 = c + h[0] * r[3] + h[1] * r[2] + h[2] * r[1] + h[3] * r[0] + h[4] * (5L * r[9]) + h[5] * (5L * r[8]) + h[6] * (5L * r[7]) + h[7] * (5L * r[6]) + h[8] * (5L * r[5]) + h[9] * (5L * r[4]); c = d3 ushr 13
                val d4 = c + h[0] * r[4] + h[1] * r[3] + h[2] * r[2] + h[3] * r[1] + h[4] * r[0] + h[5] * (5L * r[9]) + h[6] * (5L * r[8]) + h[7] * (5L * r[7]) + h[8] * (5L * r[6]) + h[9] * (5L * r[5]); c = d4 ushr 13
                val d5 = c + h[0] * r[5] + h[1] * r[4] + h[2] * r[3] + h[3] * r[2] + h[4] * r[1] + h[5] * r[0] + h[6] * (5L * r[9]) + h[7] * (5L * r[8]) + h[8] * (5L * r[7]) + h[9] * (5L * r[6]); c = d5 ushr 13
                val d6 = c + h[0] * r[6] + h[1] * r[5] + h[2] * r[4] + h[3] * r[3] + h[4] * r[2] + h[5] * r[1] + h[6] * r[0] + h[7] * (5L * r[9]) + h[8] * (5L * r[8]) + h[9] * (5L * r[7]); c = d6 ushr 13
                val d7 = c + h[0] * r[7] + h[1] * r[6] + h[2] * r[5] + h[3] * r[4] + h[4] * r[3] + h[5] * r[2] + h[6] * r[1] + h[7] * r[0] + h[8] * (5L * r[9]) + h[9] * (5L * r[8]); c = d7 ushr 13
                val d8 = c + h[0] * r[8] + h[1] * r[7] + h[2] * r[6] + h[3] * r[5] + h[4] * r[4] + h[5] * r[3] + h[6] * r[2] + h[7] * r[1] + h[8] * r[0] + h[9] * (5L * r[9]); c = d8 ushr 13
                val d9 = c + h[0] * r[9] + h[1] * r[8] + h[2] * r[7] + h[3] * r[6] + h[4] * r[5] + h[5] * r[4] + h[6] * r[3] + h[7] * r[2] + h[8] * r[1] + h[9] * r[0]; c = d9 ushr 13

                h[0] = d0 and 0x1fffL; h[1] = d1 and 0x1fffL; h[2] = d2 and 0x1fffL; h[3] = d3 and 0x1fffL; h[4] = d4 and 0x1fffL
                h[5] = d5 and 0x1fffL; h[6] = d6 and 0x1fffL; h[7] = d7 and 0x1fffL; h[8] = d8 and 0x1fffL; h[9] = (d9 and 0x1fffL) + (c * 5L)
                c = h[9] ushr 13; h[9] = h[9] and 0x1fffL; h[0] += c * 5L

                mpos += 16
                rem -= 16
            }
        }

        fun finish(mac: ByteArray, macpos: Int) {
            if (leftover > 0) {
                buffer[leftover] = 1
                for (i in (leftover + 1) until 16) buffer[i] = 0
                fin = 1
                blocks(buffer, 0, 16)
            }

            var c = h[1] ushr 13; h[1] = h[1] and 0x1fffL; h[2] += c
            c = h[2] ushr 13; h[2] = h[2] and 0x1fffL; h[3] += c
            c = h[3] ushr 13; h[3] = h[3] and 0x1fffL; h[4] += c
            c = h[4] ushr 13; h[4] = h[4] and 0x1fffL; h[5] += c
            c = h[5] ushr 13; h[5] = h[5] and 0x1fffL; h[6] += c
            c = h[6] ushr 13; h[6] = h[6] and 0x1fffL; h[7] += c
            c = h[7] ushr 13; h[7] = h[7] and 0x1fffL; h[8] += c
            c = h[8] ushr 13; h[8] = h[8] and 0x1fffL; h[9] += c
            c = h[9] ushr 13; h[9] = h[9] and 0x1fffL; h[0] += c * 5L
            c = h[0] ushr 13; h[0] = h[0] and 0x1fffL; h[1] += c

            val g0 = h[0] + 5L; c = g0 ushr 13
            val g1 = h[1] + c; c = g1 ushr 13
            val g2 = h[2] + c; c = g2 ushr 13
            val g3 = h[3] + c; c = g3 ushr 13
            val g4 = h[4] + c; c = g4 ushr 13
            val g5 = h[5] + c; c = g5 ushr 13
            val g6 = h[6] + c; c = g6 ushr 13
            val g7 = h[7] + c; c = g7 ushr 13
            val g8 = h[8] + c; c = g8 ushr 13
            val g9 = h[9] + c - (1L shl 13)

            val mask = (g9 ushr 63).inv() + 1L // if g9 >= 0 then 0 else -1
            val nmask = mask.inv()

            h[0] = (h[0] and nmask) or ((g0 and 0x1fffL) and mask)
            h[1] = (h[1] and nmask) or ((g1 and 0x1fffL) and mask)
            h[2] = (h[2] and nmask) or ((g2 and 0x1fffL) and mask)
            h[3] = (h[3] and nmask) or ((g3 and 0x1fffL) and mask)
            h[4] = (h[4] and nmask) or ((g4 and 0x1fffL) and mask)
            h[5] = (h[5] and nmask) or ((g5 and 0x1fffL) and mask)
            h[6] = (h[6] and nmask) or ((g6 and 0x1fffL) and mask)
            h[7] = (h[7] and nmask) or ((g7 and 0x1fffL) and mask)
            h[8] = (h[8] and nmask) or ((g8 and 0x1fffL) and mask)
            h[9] = (h[9] and nmask) or ((g9 and 0x1fffL) and mask)

            val f0 = (h[0] or (h[1] shl 13)) + pad[0]; c = f0 ushr 16
            val f1 = ((h[1] ushr 3) or (h[2] shl 10)) + pad[1] + c; c = f1 ushr 16
            val f2 = ((h[2] ushr 6) or (h[3] shl 7)) + pad[2] + c; c = f2 ushr 16
            val f3 = ((h[3] ushr 9) or (h[4] shl 4)) + pad[3] + c; c = f3 ushr 16
            val f4 = ((h[4] ushr 12) or (h[5] shl 1) or (h[6] shl 14)) + pad[4] + c; c = f4 ushr 16
            val f5 = ((h[6] ushr 2) or (h[7] shl 11)) + pad[5] + c; c = f5 ushr 16
            val f6 = ((h[7] ushr 5) or (h[8] shl 8)) + pad[6] + c; c = f6 ushr 16
            val f7 = ((h[8] ushr 8) or (h[9] shl 5)) + pad[7] + c

            mac[macpos + 0] = (f0 ushr 0).toByte(); mac[macpos + 1] = (f0 ushr 8).toByte()
            mac[macpos + 2] = (f1 ushr 0).toByte(); mac[macpos + 3] = (f1 ushr 8).toByte()
            mac[macpos + 4] = (f2 ushr 0).toByte(); mac[macpos + 5] = (f2 ushr 8).toByte()
            mac[macpos + 6] = (f3 ushr 0).toByte(); mac[macpos + 7] = (f3 ushr 8).toByte()
            mac[macpos + 8] = (f4 ushr 0).toByte(); mac[macpos + 9] = (f4 ushr 8).toByte()
            mac[macpos + 10] = (f5 ushr 0).toByte(); mac[macpos + 11] = (f5 ushr 8).toByte()
            mac[macpos + 12] = (f6 ushr 0).toByte(); mac[macpos + 13] = (f6 ushr 8).toByte()
            mac[macpos + 14] = (f7 ushr 0).toByte(); mac[macpos + 15] = (f7 ushr 8).toByte()
        }
    }
}

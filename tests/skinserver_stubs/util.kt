package android.util
object Base64 { const val NO_WRAP = 2; fun encodeToString(b: ByteArray, flags: Int) = java.util.Base64.getEncoder().encodeToString(b) }
object Log { fun w(t: String, msg: String) = 0; fun e(t: String, msg: String, th: Throwable) = 0 }

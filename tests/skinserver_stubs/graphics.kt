package android.graphics
object BitmapFactory {
 class Options { var inJustDecodeBounds = false; var outWidth = 64; var outHeight = 64 }
 fun decodeByteArray(b: ByteArray, offset: Int, len: Int, opts: Options) = Unit
}

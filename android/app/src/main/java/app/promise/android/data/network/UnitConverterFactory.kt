package app.promise.android.data.network

import java.lang.reflect.Type
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

class UnitConverterFactory : Converter.Factory() {
    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type != Unit::class.java) return null
        return Converter<ResponseBody, Unit> { body ->
            body.close()
            Unit
        }
    }
}

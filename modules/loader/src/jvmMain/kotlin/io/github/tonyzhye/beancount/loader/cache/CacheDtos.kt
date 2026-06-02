package io.github.tonyzhye.beancount.loader.cache

import io.github.tonyzhye.beancount.core.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for Decimal type.
 * Stores as plain string representation.
 */
object DecimalSerializer : KSerializer<Decimal> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Decimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Decimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder): Decimal = Decimal(decoder.decodeString())
}

/**
 * Serializer for LocalDate type.
 */
object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate {
        val str = decoder.decodeString()
        val parts = str.split("-")
        return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }
}

// ===== DTOs for serialization =====

@Serializable
 data class CacheEntryDto(
    val version: Int = 1,
    val entries: List<DirectiveDto>,
    val errors: List<ErrorDto>,
    val options: OptionsDto,
    val sourceFiles: Map<String, Long>  // filename -> lastModified
)

@Serializable
sealed class DirectiveDto {
    abstract val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>
    abstract val date: @Serializable(with = LocalDateSerializer::class) LocalDate
}

@Serializable
 data class TransactionDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val flag: String,
    val payee: String?,
    val narration: String?,
    val tags: Set<String>,
    val links: Set<String>,
    val postings: List<PostingDto>
) : DirectiveDto()

@Serializable
 data class OpenDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String,
    val currencies: List<String>,
    val booking: String?
) : DirectiveDto()

@Serializable
 data class CloseDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String
) : DirectiveDto()

@Serializable
 data class BalanceDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String,
    val amount: AmountDto,
    val tolerance: @Serializable(with = DecimalSerializer::class) Decimal?,
    val diffAmount: AmountDto?
) : DirectiveDto()

@Serializable
 data class PriceDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val currency: String,
    val amount: AmountDto
) : DirectiveDto()

@Serializable
 data class CommodityDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val currency: String
) : DirectiveDto()

@Serializable
 data class NoteDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String,
    val comment: String
) : DirectiveDto()

@Serializable
 data class EventDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val type: String,
    val description: String
) : DirectiveDto()

@Serializable
 data class QueryDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val name: String,
    val queryString: String
) : DirectiveDto()

@Serializable
 data class PadDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String,
    val sourceAccount: String
) : DirectiveDto()

@Serializable
 data class DocumentDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val account: String,
    val filename: String,
    val tags: Set<String>?,
    val links: Set<String>?
) : DirectiveDto()

@Serializable
 data class CustomDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val type: String,
    val values: List<@Serializable(with = AnyValueSerializer::class) Any>
) : DirectiveDto()

@Serializable
 data class IncludeDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val filename: String
) : DirectiveDto()

@Serializable
 data class PushTagDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val tag: String
) : DirectiveDto()

@Serializable
 data class PopTagDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val tag: String
) : DirectiveDto()

@Serializable
 data class PushMetaDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val key: String,
    val value: @Serializable(with = AnyValueSerializer::class) Any
) : DirectiveDto()

@Serializable
 data class PopMetaDto(
    override val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val date: @Serializable(with = LocalDateSerializer::class) LocalDate,
    val key: String
) : DirectiveDto()

@Serializable
 data class PostingDto(
    val account: String,
    val units: AmountDto?,
    val cost: CostSpecDto?,
    val price: AmountDto?,
    val flag: String?,
    val meta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>?
)

@Serializable
 data class AmountDto(
    val number: @Serializable(with = DecimalSerializer::class) Decimal,
    val currency: String
)

@Serializable
 data class CostSpecDto(
    val numberPer: @Serializable(with = DecimalSerializer::class) Decimal?,
    val numberTotal: @Serializable(with = DecimalSerializer::class) Decimal?,
    val currency: String?,
    val date: @Serializable(with = LocalDateSerializer::class) LocalDate?,
    val label: String?,
    val mergeCost: Boolean
)

@Serializable
sealed class ErrorDto {
    abstract val sourceMeta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>
    abstract val message: String
}

@Serializable
 data class LoadErrorDto(
    override val sourceMeta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val message: String,
    val entry: DirectiveDto?
) : ErrorDto()

@Serializable
 data class ValidationErrorDto(
    override val sourceMeta: Map<String, @Serializable(with = AnyValueSerializer::class) Any>,
    override val message: String,
    val entry: DirectiveDto?
) : ErrorDto()

@Serializable
 data class OptionsDto(
    val title: String,
    val accountTypes: AccountTypesConfigDto,
    val operatingCurrencies: List<String>,
    val documents: List<String>,
    val include: List<String>,
    val plugin: List<PluginSpecDto>,
    val autoPluginsEnabled: Boolean,
    val toleranceMap: Map<String, @Serializable(with = DecimalSerializer::class) Decimal>
)

@Serializable
 data class AccountTypesConfigDto(
    val assets: String,
    val liabilities: String,
    val equity: String,
    val income: String,
    val expenses: String
)

@Serializable
 data class PluginSpecDto(
    val moduleName: String,
    val config: String?
)

/**
 * Serializer for Meta values (Any).
 * Supports: String, Int, Long, Double, Boolean, Decimal, LocalDate
 */
object AnyValueSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AnyValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Any) {
        when (value) {
            is String -> encoder.encodeString(value)
            is Int -> encoder.encodeInt(value)
            is Long -> encoder.encodeLong(value)
            is Double -> encoder.encodeDouble(value)
            is Boolean -> encoder.encodeBoolean(value)
            is Decimal -> encoder.encodeString(value.toPlainString())
            is LocalDate -> encoder.encodeString(value.toString())
            else -> encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        // Try to decode as string, then parse common formats
        val str = decoder.decodeString()
        return when {
            str == "true" || str == "false" -> str.toBoolean()
            str.contains(".") && str.toDoubleOrNull() != null -> Decimal(str)
            str.toIntOrNull() != null -> str.toInt()
            str.toLongOrNull() != null -> str.toLong()
            str.matches(Regex("""\d{4}-\d{2}-\d{2}""")) -> {
                val parts = str.split("-")
                LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
            else -> str
        }
    }
}

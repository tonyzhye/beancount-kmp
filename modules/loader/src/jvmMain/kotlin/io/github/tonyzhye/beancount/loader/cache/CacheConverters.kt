package io.github.tonyzhye.beancount.loader.cache

import io.github.tonyzhye.beancount.core.*

// ===== Directive to DTO =====

fun Directive.toDto(): DirectiveDto = when (this) {
    is Transaction -> TransactionDto(
        meta = meta,
        date = date,
        flag = flag,
        payee = payee,
        narration = narration,
        tags = tags,
        links = links,
        postings = postings.map { it.toDto() }
    )
    is Open -> OpenDto(
        meta = meta,
        date = date,
        account = account,
        currencies = currencies,
        booking = booking?.name
    )
    is Close -> CloseDto(
        meta = meta,
        date = date,
        account = account
    )
    is Balance -> BalanceDto(
        meta = meta,
        date = date,
        account = account,
        amount = amount.toDto(),
        tolerance = tolerance,
        diffAmount = diffAmount?.toDto()
    )
    is Price -> PriceDto(
        meta = meta,
        date = date,
        currency = currency,
        amount = amount.toDto()
    )
    is Commodity -> CommodityDto(
        meta = meta,
        date = date,
        currency = currency
    )
    is Note -> NoteDto(
        meta = meta,
        date = date,
        account = account,
        comment = comment
    )
    is Event -> EventDto(
        meta = meta,
        date = date,
        type = type,
        description = description
    )
    is Query -> QueryDto(
        meta = meta,
        date = date,
        name = name,
        queryString = queryString
    )
    is Pad -> PadDto(
        meta = meta,
        date = date,
        account = account,
        sourceAccount = sourceAccount
    )
    is Document -> DocumentDto(
        meta = meta,
        date = date,
        account = account,
        filename = filename,
        tags = tags,
        links = links
    )
    is Custom -> CustomDto(
        meta = meta,
        date = date,
        type = type,
        values = values
    )
    is Include -> IncludeDto(
        meta = meta,
        date = date,
        filename = filename
    )
}

fun Posting.toDto(): PostingDto = PostingDto(
    account = account,
    units = units?.toDto(),
    cost = cost?.toDto(),
    price = price?.toDto(),
    flag = flag,
    meta = meta
)

fun Amount.toDto(): AmountDto = AmountDto(number = number, currency = currency)

fun CostSpec.toDto(): CostSpecDto = CostSpecDto(
    numberPer = numberPer,
    numberTotal = numberTotal,
    currency = currency,
    date = date,
    label = label,
    mergeCost = mergeCost
)

fun BeancountError.toDto(): ErrorDto = when (this) {
    is LoadError -> LoadErrorDto(
        sourceMeta = source,
        message = message,
        entry = entry?.toDto()
    )
    is ValidationError -> ValidationErrorDto(
        sourceMeta = source,
        message = message,
        entry = entry?.toDto()
    )
    else -> LoadErrorDto(
        sourceMeta = source,
        message = message,
        entry = entry?.toDto()
    )
}

fun Options.toDto(): OptionsDto = OptionsDto(
    title = title,
    accountTypes = accountTypes.toDto(),
    operatingCurrencies = operatingCurrencies,
    documents = documents,
    include = include,
    plugin = plugin.map { PluginSpecDto(it.moduleName, it.config) },
    autoPluginsEnabled = autoPluginsEnabled,
    toleranceMap = toleranceMap
)

fun AccountTypesConfig.toDto(): AccountTypesConfigDto = AccountTypesConfigDto(
    assets = assets,
    liabilities = liabilities,
    equity = equity,
    income = income,
    expenses = expenses
)

// ===== DTO to Domain =====

fun DirectiveDto.toDomain(): Directive = when (this) {
    is TransactionDto -> Transaction(
        meta = meta,
        date = date,
        flag = flag,
        payee = payee,
        narration = narration,
        tags = tags,
        links = links,
        postings = postings.map { it.toDomain() }
    )
    is OpenDto -> Open(
        meta = meta,
        date = date,
        account = account,
        currencies = currencies,
        booking = booking?.let { Booking.valueOf(it) }
    )
    is CloseDto -> Close(
        meta = meta,
        date = date,
        account = account
    )
    is BalanceDto -> Balance(
        meta = meta,
        date = date,
        account = account,
        amount = amount.toDomain(),
        tolerance = tolerance,
        diffAmount = diffAmount?.toDomain()
    )
    is PriceDto -> Price(
        meta = meta,
        date = date,
        currency = currency,
        amount = amount.toDomain()
    )
    is CommodityDto -> Commodity(
        meta = meta,
        date = date,
        currency = currency
    )
    is NoteDto -> Note(
        meta = meta,
        date = date,
        account = account,
        comment = comment
    )
    is EventDto -> Event(
        meta = meta,
        date = date,
        type = type,
        description = description
    )
    is QueryDto -> Query(
        meta = meta,
        date = date,
        name = name,
        queryString = queryString
    )
    is PadDto -> Pad(
        meta = meta,
        date = date,
        account = account,
        sourceAccount = sourceAccount
    )
    is DocumentDto -> Document(
        meta = meta,
        date = date,
        account = account,
        filename = filename,
        tags = tags,
        links = links
    )
    is CustomDto -> Custom(
        meta = meta,
        date = date,
        type = type,
        values = values
    )
    is IncludeDto -> Include(
        meta = meta,
        date = date,
        filename = filename
    )
}

fun PostingDto.toDomain(): Posting = Posting(
    account = account,
    units = units?.toDomain(),
    cost = cost?.toDomain(),
    price = price?.toDomain(),
    flag = flag,
    meta = meta
)

fun AmountDto.toDomain(): Amount = Amount(number = number, currency = currency)

fun CostSpecDto.toDomain(): CostSpec = CostSpec(
    numberPer = numberPer,
    numberTotal = numberTotal,
    currency = currency,
    date = date,
    label = label,
    mergeCost = mergeCost
)

fun ErrorDto.toDomain(): BeancountError = when (this) {
    is LoadErrorDto -> LoadError(
        source = sourceMeta,
        message = message,
        entry = entry?.toDomain()
    )
    is ValidationErrorDto -> ValidationError(
        source = sourceMeta,
        message = message,
        entry = entry?.toDomain()
    )
}

fun OptionsDto.toDomain(): Options = Options(
    title = title,
    accountTypes = accountTypes.toDomain(),
    operatingCurrencies = operatingCurrencies,
    documents = documents,
    include = include,
    plugin = plugin.map { PluginSpec(it.moduleName, it.config) },
    autoPluginsEnabled = autoPluginsEnabled,
    toleranceMap = toleranceMap
)

fun AccountTypesConfigDto.toDomain(): AccountTypesConfig = AccountTypesConfig(
    assets = assets,
    liabilities = liabilities,
    equity = equity,
    income = income,
    expenses = expenses
)

// ===== LoadResult to/from DTO =====

fun LoadResult.toDto(sourceFiles: Map<String, Long>): CacheEntryDto = CacheEntryDto(
    entries = entries.map { it.toDto() },
    errors = errors.map { it.toDto() },
    options = options.toDto(),
    sourceFiles = sourceFiles
)

fun CacheEntryDto.toDomain(): LoadResult = LoadResult(
    entries = entries.map { it.toDomain() },
    errors = errors.map { it.toDomain() },
    options = options.toDomain()
)

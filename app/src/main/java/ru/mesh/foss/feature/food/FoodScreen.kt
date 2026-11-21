@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package ru.mesh.foss.feature.food

import ru.mesh.foss.core.*
import ru.mesh.foss.data.*
import ru.mesh.foss.ui.*
import ru.mesh.foss.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Собранные данные экрана питания за один проход. */
private data class FoodData(
    val state: JSONObject,
    val balances: JSONArray,
    val menu: JSONArray,
    val orders: JSONArray
)

/**
 * Нативный раздел «Еда и баланс»: состояние питания, баланс лицевого счёта,
 * меню комплексных обедов на выбранный день и предзаказы.
 *
 * Данные берутся из тех же API МЭШ, что и веб-страница `/diary/school/food`.
 */
@Composable
fun FoodScreen(store: SessionStore, client: MeshClient, personId: String?) {
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(MeshCore.defaultFeedingDate()) }
    var loading by remember { mutableStateOf(personId != null) }
    // Обновление свайпом «потянуть»: контент остаётся, крутится индикатор.
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf<JSONObject?>(null) }
    var balances by remember { mutableStateOf(JSONArray()) }
    var menuDays by remember { mutableStateOf<JSONArray?>(null) }
    var orders by remember { mutableStateOf(JSONArray()) }
    // Режим оплаты: false — платное (`paymentTypes` = 1), true — льготное (= 0).
    var freeMeal by remember { mutableStateOf(false) }
    var selectedKind by remember { mutableStateOf<Int?>(null) }

    fun load() {
        if (personId.isNullOrBlank()) {
            loading = false
            refreshing = false
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    FoodData(
                        state = client.getFoodState(personId),
                        balances = client.getFoodBalance(personId),
                        menu = client.getFoodMenu(personId, selectedDate, selectedDate),
                        orders = client.getFoodOrders(personId, selectedDate, selectedDate)
                    )
                }
                state = data.state
                balances = data.balances
                menuDays = data.menu
                orders = data.orders
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = MeshCore.describeError(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(personId, selectedDate) { load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .swipeDays(
                onPrev = {
                    val week = state?.optJSONObject("feedingWeek")
                    selectedDate = MeshCore.nextFeedingDate(selectedDate, week, -1)
                },
                onNext = {
                    val week = state?.optJSONObject("feedingWeek")
                    selectedDate = MeshCore.nextFeedingDate(selectedDate, week, 1)
                }
            )
    ) {
        DaySelector(
            date = selectedDate,
            onPrev = {
                val week = state?.optJSONObject("feedingWeek")
                selectedDate = MeshCore.nextFeedingDate(selectedDate, week, -1)
            },
            onNext = {
                val week = state?.optJSONObject("feedingWeek")
                selectedDate = MeshCore.nextFeedingDate(selectedDate, week, 1)
            },
            onDateSelected = { selectedDate = it }
        )
        Spacer(Modifier.height(4.dp))

        when {
            personId.isNullOrBlank() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(Words.LoadingStudentProfile.text)
                }
            }
            loading && !refreshing -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            error != null -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { load() }) { Text(Words.Retry.text) }
            }
            else -> {
                val day = MeshCore.foodDayFor(menuDays, selectedDate)
                val all = day?.optJSONArray("complexes") ?: JSONArray()
                val complexes = MeshCore.foodComplexesFor(all, freeMeal)
                val kinds = MeshCore.foodMealKinds.filter { kind ->
                    (0 until complexes.length()).any {
                        complexes.getJSONObject(it).optInt("kind", -1) == kind
                    }
                }
                val activeKind =
                    if (selectedKind != null && kinds.contains(selectedKind)) selectedKind
                    else kinds.firstOrNull()
                val mealList = (0 until complexes.length())
                    .map { complexes.getJSONObject(it) }
                    .filter { it.optInt("kind", -1) == activeKind }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { refreshing = true; load() },
                    modifier = Modifier.weight(1f)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { BalanceCard(balances) }
                    item { StateCard(state) }

                    item {
                        SectionTitle(Words.DailyMenu.text)
                    }
                    // Платное / льготное питание: M3E connected-переключатель.
                    item {
                        val paidLabel = Words.Paid.text
                        val freeLabel = Words.Subsidised.text
                        ButtonGroup(
                            overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            toggleableItem(
                                checked = !freeMeal,
                                label = paidLabel,
                                onCheckedChange = { freeMeal = false },
                                weight = 1f
                            )
                            toggleableItem(
                                checked = freeMeal,
                                label = freeLabel,
                                onCheckedChange = { freeMeal = true },
                                weight = 1f
                            )
                        }
                    }
                    // Приёмы пищи, доступные в выбранном режиме.
                    if (kinds.size > 1) {
                        item {
                            val labels = kinds.associateWith { mealLabel(it) }
                            ButtonGroup(
                                overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                kinds.forEach { kind ->
                                    toggleableItem(
                                        checked = kind == activeKind,
                                        label = labels[kind] ?: "",
                                        onCheckedChange = { selectedKind = kind },
                                        weight = 1f
                                    )
                                }
                            }
                        }
                    }
                    if (complexes.length() == 0 || mealList.isEmpty()) {
                        item {
                            Text(
                                Words.NoMenuForThisDay.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        itemsIndexed(mealList) { index, complex ->
                            // На «Льготном» цены не показываем; первый блок
                            // (основной) развёрнут по умолчанию.
                            ComplexCard(
                                complex = complex,
                                showPrice = !freeMeal,
                                expandedByDefault = index == 0
                            )
                        }
                    }

                    if (orders.length() > 0) {
                        item { SectionTitle(Words.PreOrders.text) }
                        items((0 until orders.length()).map { orders.getJSONObject(it) }) { order ->
                            OrderRow(order)
                        }
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }
}

/** Локализованное название приёма пищи по коду `kind`. */
@Composable
private fun mealLabel(kind: Int): String = when (kind) {
    0 -> Words.Breakfast.text
    1 -> Words.MorningSnack.text
    2 -> Words.Lunch.text
    3 -> Words.AfternoonSnack.text
    4 -> Words.Dinner.text
    else -> Words.Meal.text
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun BalanceCard(balances: JSONArray) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        // Ключевая цифра экрана — самый светлый контейнер (M3E: containment).
        tint = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                Words.AccountBalance.text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            if (balances.length() == 0) {
                Text(Words.Unavailable.text, style = MaterialTheme.typography.bodyMedium)
            } else {
                for (i in 0 until balances.length()) {
                    val entry = balances.optJSONObject(i) ?: continue
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            MeshCore.formatMoney(entry.optLong("balance", 0L)),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        val limit = entry.opt("expenseDayLimit")
                        if (limit != null && limit != JSONObject.NULL) {
                            Text(
                                Words.DailyLimit.text + MeshCore.formatMoney(entry.optLong("expenseDayLimit", 0L)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateCard(state: JSONObject?) {
    val src = state ?: return
    val addresses = src.optJSONArray("addresses") ?: JSONArray()
    val first = addresses.optJSONObject(0)
    val name = first?.optString("organizationName").orEmpty()
    val address = first?.optString("text").orEmpty()
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        tint = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (name.isNotBlank()) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
            if (address.isNotBlank()) {
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            InfoLine(Words.PreOrder.text, src.optBoolean("preorderAllowed", false))
            InfoLine(Words.SubsidisedMeals.text, src.optBoolean("discountedMeals", false))
            FeedingWeekRow(src.optJSONObject("feedingWeek"))
        }
    }
}

@Composable
private fun InfoLine(label: String, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            if (enabled) Words.Available.text else Words.UnavailableLabel.text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeedingWeekRow(week: JSONObject?) {
    if (week == null || week.length() == 0) return
    val days = listOf(
        "monday" to Words.Mon.text, "tuesday" to Words.Tue.text, "wednesday" to Words.Wed.text,
        "thursday" to Words.Thu.text, "friday" to Words.Fri.text, "saturday" to Words.Sat.text, "sunday" to Words.Sun.text
    )
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEach { (key, label) ->
            val enabled = week.optBoolean(key, false)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ComplexCard(complex: JSONObject, showPrice: Boolean, expandedByDefault: Boolean) {
    var expanded by remember(complex.opt("id"), expandedByDefault) { mutableStateOf(expandedByDefault) }
    val dishes = complex.optJSONArray("dishes") ?: JSONArray()
    val kindLabel = complex.optString("kindLabel")
    val priceLabel = complex.optString("priceLabel")
    val mealLabel = Words.Meal.text
    val gramLabel = Words.G.text
    val kcalLabel = Words.Kcal.text
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        tint = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = dishes.length() > 0) { expanded = !expanded }
                .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        complex.optString("name").ifBlank { mealLabel },
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOf(kindLabel, Words.Dishes.text + dishes.length()).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (showPrice) Text(priceLabel, fontWeight = FontWeight.Bold)
                if (dishes.length() > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(if (expanded) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                dishes.let { list ->
                    for (i in 0 until list.length()) {
                        val dish = list.optJSONObject(i) ?: continue
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                dish.optString("name").ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            val extras = buildList {
                                val weight = dish.optString("weight")
                                if (weight.isNotBlank() && weight != "0") add("$weight $gramLabel")
                                val kcal = dish.optInt("calories", 0)
                                if (kcal > 0) add("$kcal $kcalLabel")
                            }.joinToString(" · ")
                            if (extras.isNotBlank()) {
                                Text(
                                    extras,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: JSONObject) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        tint = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val id = order.opt("id")
                Text(
                    Words.Order.text + if (id != null && id != JSONObject.NULL) " №$id" else "",
                    fontWeight = FontWeight.Medium
                )
                val date = order.optString("onDate").ifBlank { order.optString("date") }
                if (date.isNotBlank()) {
                    Text(
                        MeshCore.dateShort(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val sum = firstMoney(order)
            if (sum != null) Text(MeshCore.formatMoney(sum), fontWeight = FontWeight.Bold)
        }
    }
}

/** Первая денежная сумма заказа из известных ключей (в копейках). */
private fun firstMoney(order: JSONObject): Long? {
    listOf("amount", "totalAmount", "totalPrice", "price", "sum").forEach { key ->
        if (order.has(key) && !order.isNull(key)) return order.optLong(key, 0L)
    }
    return null
}

package com.neko7ina.wallet.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neko7ina.wallet.assistant.core.model.TravelDocument
import com.neko7ina.wallet.assistant.core.model.TravelSegment
import com.neko7ina.wallet.assistant.core.parser.ChinaRailwayEmailParser
import com.neko7ina.wallet.assistant.core.parser.ParseResult
import com.neko7ina.wallet.assistant.core.parser.RawDocument
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TravelWalletApp() }
    }
}

private enum class Screen {
    HOME,
    IMPORT,
    CONFIRM,
}

@Composable
private fun TravelWalletApp(viewModel: TravelWalletViewModel = viewModel()) {
    val savedDocuments by viewModel.documents.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var emailBody by rememberSaveable { mutableStateOf("") }
    var parseError by rememberSaveable { mutableStateOf<String?>(null) }
    var parsedDocument by remember { mutableStateOf<TravelDocument?>(null) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    documents = savedDocuments,
                    onImport = {
                        emailBody = ""
                        parseError = null
                        screen = Screen.IMPORT
                    },
                )

                Screen.IMPORT -> ImportScreen(
                    emailBody = emailBody,
                    error = parseError,
                    onBodyChange = {
                        emailBody = it
                        parseError = null
                    },
                    onUseSample = {
                        emailBody = SAMPLE_EMAIL
                        parseError = null
                    },
                    onBack = { screen = Screen.HOME },
                    onParse = {
                        when (val result = ChinaRailwayEmailParser().parse(RawDocument(body = emailBody))) {
                            is ParseResult.Success -> {
                                parsedDocument = result.document
                                parseError = null
                                screen = Screen.CONFIRM
                            }

                            is ParseResult.Failure -> parseError = result.message
                        }
                    },
                )

                Screen.CONFIRM -> ConfirmationScreen(
                    document = requireNotNull(parsedDocument),
                    onBack = { screen = Screen.IMPORT },
                    onSave = {
                        viewModel.save(requireNotNull(parsedDocument))
                        screen = Screen.HOME
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    documents: List<TravelDocument>,
    onImport: () -> Unit,
) {
    if (documents.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("还没有行程", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "导入购票邮件后，行程信息会显示在这里。",
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onImport) {
                Text("导入邮件正文")
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("我的行程", style = MaterialTheme.typography.headlineMedium)
            documents.forEach { document ->
                TripCard(
                    document = document,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp),
            ) {
                Text("导入另一封邮件")
            }
        }
    }
}

@Composable
private fun ImportScreen(
    emailBody: String,
    error: String?,
    onBodyChange: (String) -> Unit,
    onUseSample: () -> Unit,
    onBack: () -> Unit,
    onParse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("导入邮件正文", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "粘贴购票成功邮件的正文，解析后请核对乘车信息。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = emailBody,
            onValueChange = onBodyChange,
            label = { Text("邮件正文") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp)
                .padding(top = 20.dp),
        )
        TextButton(
            onClick = onUseSample,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("填入示例")
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
            Button(
                onClick = onParse,
                enabled = emailBody.isNotBlank(),
            ) {
                Text("解析邮件")
            }
        }
    }
}

@Composable
private fun ConfirmationScreen(
    document: TravelDocument,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text("确认行程", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "请确认邮件中的信息是否识别正确。",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        TripCard(
            document = document,
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("返回修改")
            }
            Button(onClick = onSave) {
                Text("保存行程")
            }
        }
    }
}

@Composable
private fun TripCard(
    document: TravelDocument,
    modifier: Modifier = Modifier,
) {
    val segment = document.segments.first()
    val seat = segment.seatAssignments.first()
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${segment.serviceNumber} · ${segment.origin.name} → ${segment.destination.name}",
                style = MaterialTheme.typography.titleLarge,
            )
            DetailRow("出发", segment.departureTime.format(DEPARTURE_FORMAT))
            DetailRow("座位", "${seat.section} 车 ${seat.seat}")
            DetailRow("席别", seat.category)
            DetailRow("乘车人", document.travelers.first().name)
            DetailRow("订单", document.reservation.reference)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.Gray)
        Text(value)
    }
}

private val DEPARTURE_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 HH:mm")

private val SAMPLE_EMAIL = """
    尊敬的 测试乘客先生：
    您好！
    您于2026年02月15日在中国铁路客户服务中心网站(12306.cn) 成功购买了1张车票，票款共计120.00元，订单号码 E000000000 。 所购车票信息如下：
    1.测试乘客，2026年02月21日13:10开，镇江站-上海站，G7229次列车，2车17C号，二等座，成人票，票价120.0元，电子客票。
""".trimIndent()

package com.rasmi.purevon.presentation.screen.settings

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rasmi.purevon.R
import com.rasmi.purevon.util.viral.MyCardData
import com.rasmi.purevon.util.viral.QrContactCard
import com.rasmi.purevon.util.viral.QrCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** خطوتا تجربة بطاقتي: نموذج البيانات ثم معاينة البطاقة الفعلية */
private enum class MyCardStep { FORM, PREVIEW }

/**
 * ✅ VIRAL #4b — بطاقتي كورقة سفلية احترافية بخطوتين وبطاقة واحدة:
 * 1) النموذج: تعديل بيانات المستخدم ثم حفظ (التعديل يكفي لإدخال الرقم المطلوب)
 * 2) المعاينة: صورة البطاقة المولدة فعلًا وتحتها [تعديل] [مشاركة]
 * تفتح الورقة موسّعة بالكامل (skipPartiallyExpanded)، وإن وُجدت بيانات مكتملة
 * محفوظة تبدأ مباشرة على المعاينة (أسرع طريق للمشاركة).
 * الرمز يحمل vCard كاملًا، والبطاقة تعرض آخر 4 أرقام فقط (نمط سناب شات).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCardSheet(
    initialFirstName: String,
    initialLastName: String,
    initialPhone: String,
    initialEmail: String,
    initialCompany: String,
    onSave: (firstName: String, lastName: String, phone: String, email: String, company: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialData = MyCardData(initialFirstName, initialLastName, initialPhone, initialEmail, initialCompany)

    var firstName by remember { mutableStateOf(initialFirstName) }
    var lastName by remember { mutableStateOf(initialLastName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var email by remember { mutableStateOf(initialEmail) }
    var company by remember { mutableStateOf(initialCompany) }

    var step by remember {
        mutableStateOf(if (initialData.isShareable()) MyCardStep.PREVIEW else MyCardStep.FORM)
    }

    // ✅ الفتح الموسع بالكامل دون حاجة لسحب المستخدم
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        when (step) {
            MyCardStep.FORM -> MyCardFormStep(
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                email = email,
                company = company,
                onFirstNameChange = { firstName = it },
                onLastNameChange = { lastName = it },
                onPhoneChange = { phone = it },
                onEmailChange = { email = it },
                onCompanyChange = { company = it },
                onSave = {
                    onSave(firstName, lastName, phone, email, company)
                    // الانتقال إلى المعاينة هو التأكيد البصري — لا حاجة لرسالة إضافية
                    step = MyCardStep.PREVIEW
                }
            )
            MyCardStep.PREVIEW -> MyCardPreviewStep(
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                email = email,
                company = company,
                onEdit = { step = MyCardStep.FORM }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MyCardFormStep(
    firstName: String,
    lastName: String,
    phone: String,
    email: String,
    company: String,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onCompanyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.my_card_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedTextField(
            value = firstName,
            onValueChange = onFirstNameChange,
            label = { Text(stringResource(R.string.contact_detail_first_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = lastName,
            onValueChange = onLastNameChange,
            label = { Text(stringResource(R.string.contact_detail_last_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.add_contact_phone_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(stringResource(R.string.add_contact_email)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = company,
            onValueChange = onCompanyChange,
            label = { Text(stringResource(R.string.add_contact_company)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(text = stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun MyCardPreviewStep(
    firstName: String,
    lastName: String,
    phone: String,
    email: String,
    company: String,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    // ✅ المحرك يُنشأ مرة واحدة ويُعاد استخدامه للمعاينة والمشاركة
    val renderer = remember { QrCardRenderer(context.applicationContext) }
    val appName = stringResource(R.string.app_name)

    val cardData = MyCardData(firstName, lastName, phone, email, company)
    val canShareQr = remember(cardData) { cardData.isShareable() }

    // توليد الصورة خارج الخيط الرئيسي، ومفتاح التذكر يعيد التوليد عند تغيير البيانات
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, cardData, appName) {
        value = withContext(Dispatchers.Default) {
            renderer.renderCard(cardData, appName)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.my_card_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = stringResource(R.string.my_card_title),
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                CircularProgressIndicator()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.my_card_edit))
            }
            Button(
                onClick = { shareQrCard(renderer, context, bitmap, cardData, appName) },
                enabled = canShareQr && bitmap != null,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.my_card_share))
            }
        }
    }
}

/** يشارك الصورة المعروضة نفسها — لا توليد ثاني مخفي */
private fun shareQrCard(
    renderer: QrCardRenderer,
    context: android.content.Context,
    rendered: android.graphics.Bitmap?,
    data: MyCardData,
    appName: String
) {
    try {
        val card = rendered ?: renderer.renderCard(data, appName) ?: return
        val uri = renderer.writeToCache(card, QrContactCard.cardFileName(data.phone)) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        android.util.Log.e("MyCard", "QR card share failed", e)
    }
}

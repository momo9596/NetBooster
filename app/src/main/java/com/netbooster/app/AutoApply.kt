suspend fun execute(profile: RouterProfile): Boolean {
    // ... (نفس كود التحقق من الاتصال)

    // 1. تعديل DNS
    log("▶ تعديل DNS...")
    js("NB.clickMenu('${profile.dnsMenuId}')") // استخدام ID من الملف الشخصي
    if (waitForElement("NB.checkDnsExist('${profile.dnsField1Id}')", 10)) {
        js("NB.fillTpLinkDns('$dnsP', '$dnsS', '${profile.dnsField1Id}', '${profile.dnsField2Id}')")
        delay(500)
        if (js("NB.clickSave('${profile.saveButtonText}')") != "0") { // استخدام نص الزر
            delay(3000); dnsSaved = true; log("✔ تم حفظ DNS")
        }
    }

    // 2. تعديل القناة
    if (channel != null) {
        log("▶ تعديل القناة...")
        js("NB.clickMenu('${profile.wifiMenuId}')")
        if (waitForElement("NB.checkChanExist('${profile.channelFieldId}')", 10)) {
            js("NB.setTpLinkChannel($channel, '${profile.channelFieldId}')")
            delay(500)
            if (js("NB.clickSave('${profile.saveButtonText}')") != "0") {
                delay(3000); channelSaved = true; log("✔ تم حفظ القناة")
            }
        }
    }
    // ... (باقي الكود: إعادة التشغيل، إلخ)
}

package com.netbooster.app

data class RouterProfile(
    val brand: String,
    val model: String,
    val defaultIp: String,
    val dnsMenuId: String,          // معرف زر القائمة الجانبية لصفحة DNS (مثال: "menu_dhcpserver")
    val dnsField1Id: String,        // اسم أو ID حقل DNS الأساسي (مثال: "dnsserver1")
    val dnsField2Id: String,        // اسم أو ID حقل DNS الثانوي (مثال: "dnsserver2")
    val wifiMenuId: String,         // معرف زر القائمة الجانبية لصفحة الواي فاي (مثال: "menu_wlbasic")
    val channelFieldId: String,     // اسم أو ID حقل القناة (مثال: "channel")
    val saveButtonText: String      // نص زر الحفظ (مثال: "Save" أو "Apply")
)

// هنا تضع بيانات الراوترات الشائعة
val knownRouters = listOf(
    // TP-Link (الذي تم دعمه بالفعل)
    RouterProfile("TP-Link", "TL-WR840N", "192.168.8.33", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Save"),
    RouterProfile("TP-Link", "Archer VR300", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Save"),
    
    // WE (TE Data) - غالباً ZTE أو Huawei
    RouterProfile("WE", "ZTE H168N", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("WE", "Huawei HG630", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),

    // فودافون - غالباً ZTE أو Huawei
    RouterProfile("Vodafone", "ZTE H168N", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("Vodafone", "Huawei DG8045", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),

    // أورنج - غالباً ZTE أو Nokia
    RouterProfile("Orange", "ZTE H168N", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("Orange", "Nokia VDSL2", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),

    // اتصالات - غالباً ZTE أو Huawei أو Nokia
    RouterProfile("Etisalat", "ZTE H168N", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("Etisalat", "Huawei DG8045", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("Etisalat", "Nokia VDSL2", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply")
)

package com.netbooster.app

data class RouterProfile(
    val brand: String,
    val defaultIp: String,
    val dnsMenuId: String,       // معرف زر القائمة الجانبية لصفحة DNS
    val dnsField1: String,       // اسم أو ID حقل DNS الأساسي
    val dnsField2: String,       // اسم أو ID حقل DNS الثانوي
    val wifiMenuId: String,      // معرف زر القائمة الجانبية لصفحة الواي فاي
    val channelField: String,    // اسم أو ID حقل القناة
    val saveButton: String       // نص زر الحفظ
)

val knownRouters = listOf(
    // TP-Link
    RouterProfile("TP-Link", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Save"),
    RouterProfile("TP-Link", "192.168.8.33", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Save"),
    
    // WE / ZTE
    RouterProfile("ZTE", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("WE", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    
    // Vodafone / Huawei
    RouterProfile("Vodafone", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    RouterProfile("Huawei", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    
    // Orange
    RouterProfile("Orange", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    
    // Etisalat
    RouterProfile("Etisalat", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Apply"),
    
    // Generic
    RouterProfile("Generic", "192.168.1.1", "menu_dhcpserver", "dnsserver1", "dnsserver2", "menu_wlbasic", "channel", "Save")
)

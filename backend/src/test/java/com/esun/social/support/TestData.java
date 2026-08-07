package com.esun.social.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 整合測試用的資料產生器。
 *
 * <p>所有整合測試共用同一座容器與同一份種子資料，因此每個測試都必須自備不會撞號的資料，
 * 才不會因為執行順序不同而互相干擾。
 */
public final class TestData {

    /** 從 0940000000 起跳，避開 03_DML_seed_data.sql 使用的 091/092/093 號段。 */
    private static final AtomicInteger PHONE_SEQUENCE = new AtomicInteger(40_000_000);

    private TestData() {}

    /** 產生一組符合 {@code ^09\d{8}$} 且不與既有資料衝突的手機號碼。 */
    public static String uniquePhoneNumber() {
        return "09" + PHONE_SEQUENCE.incrementAndGet();
    }
}

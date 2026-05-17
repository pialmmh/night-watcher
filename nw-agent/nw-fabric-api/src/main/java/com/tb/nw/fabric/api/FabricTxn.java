package com.tb.nw.fabric.api;

import java.util.List;

public interface FabricTxn {

    TxnBuilder begin();

    interface TxnBuilder {
        TxnBuilder ifModRevisionEquals(String key, long revision);
        TxnBuilder ifValueEquals(String key, byte[] value);
        TxnBuilder thenPut(String key, byte[] value);
        TxnBuilder thenPut(String key, byte[] value, Lease lease);
        TxnBuilder thenDelete(String key);
        TxnBuilder elsePut(String key, byte[] value);
        TxnResponse commit();
    }

    interface TxnResponse {
        boolean succeeded();
        List<FabricKV.KeyValue> reads();
    }
}

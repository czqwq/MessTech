package com.MessTech.client.text.compat;

/** Font-batch boundary used to pause Angelica's batching while effect text owns GL state. */
public interface FontBatchBridge {

    int mt$suspendBatch();

    void mt$resumeBatch(int depth);

    void mt$flushBatch();
}

package com.financetracker.statementimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.financetracker.common.exception.BadRequestException;
import com.financetracker.transaction.TransactionType;
import org.junit.jupiter.api.Test;

class CsvStatementParserTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    @Test
    void supportsCommonHeadersAndNormalizesDebitAndCreditRows() {
        ParsedStatementFile parsed = parser.parse("""
                Txn Date,Narration,Withdrawal,Deposit,Ref No,Account No,Account Name
                01/03/2026,Coffee,120.50,,UPI-1,1234567890,HDFC Savings
                2026-03-02,Salary,,2000.00,NEFT-9,1234567890,HDFC Savings
                """);

        assertThat(parsed.detectedAccountNumber()).isEqualTo("1234567890");
        assertThat(parsed.detectedAccountName()).isEqualTo("HDFC Savings");
        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).type()).isEqualTo(TransactionType.EXPENSE);
        assertThat(parsed.rows().get(0).amount()).isEqualByComparingTo("120.50");
        assertThat(parsed.rows().get(1).type()).isEqualTo(TransactionType.INCOME);
        assertThat(parsed.rows().get(1).amount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void rejectsMalformedRows() {
        assertThatThrownBy(() -> parser.parse("""
                Date,Description,Debit,Credit
                2026-03-01,Bad row,10.00,20.00
                """))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("both debit and credit are populated");
    }
}

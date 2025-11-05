package com.shakur.cafehelp.Service;

import com.shakur.cafehelp.DTO.ShiftDTO;
import jooqdata.tables.Shift;
import jooqdata.tables.records.PersonRecord;
import jooqdata.tables.records.ShiftRecord;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Select;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShiftService {
    private static DSLContext dsl;
    public ShiftService() {this.dsl = dsl;}
    public List<ShiftDTO> findAllShifts() {
        return dsl.selectFrom(Shift.SHIFT)
                .fetch()
                .stream()
                .map(record -> {
                    ShiftDTO dto = new ShiftDTO();
                    dto.shiftId = record.getShiftid();
                    dto.data = record.getData();
                    dto.expenses = record.getExpenses();
                    dto.profit = record.getProfit();
                    dto.startTime = record.getStarttime();
                    dto.endTime = record.getEndtime();
                    return dto;
                }).toList();
    }

    public ShiftDTO createShift(ShiftDTO dto) {
        ShiftRecord record = dsl.newRecord(jooqdata.tables.Shift.SHIFT);
        record.setShiftid(dto.shiftId);
        record.setData(dto.data);
        record.setExpenses(dto.expenses);
        record.setProfit(dto.profit);
        record.setStarttime(dto.startTime);
        record.setEndtime(dto.endTime);
        record.store();
        return dto;
    }
    public ShiftDTO updateShift(int shiftId, ShiftDTO dto) {
        int rows = dsl.update(jooqdata.tables.Shift.SHIFT)
                .set(Shift.SHIFT.DATA, dto.data)
                .set(Shift.SHIFT.EXPENSES, dto.expenses)
                .set(Shift.SHIFT.PROFIT, dto.profit)
                .set(Shift.SHIFT.STARTTIME, dto.startTime)
                .set(Shift.SHIFT.ENDTIME, dto.endTime)
                .where(Shift.SHIFT.SHIFTID.eq(shiftId))
                .execute();

        if (rows != 1) {
            throw new RuntimeException("Update affected " + rows + " rows");
        }

        // опционально — получить обновлённую запись и вернуть DTO
        ShiftRecord updated = dsl.fetchOne(
                jooqdata.tables.Shift.SHIFT,
                Shift.SHIFT.SHIFTID.eq(shiftId)
        );

        // преобразуем в dto
        dto.shiftId = updated.getShiftid();
        dto.data = updated.getData();
        dto.expenses = updated.getExpenses();
        dto.profit = updated.getProfit();
        dto.startTime = updated.getStarttime();
        dto.endTime = updated.getEndtime();

        return dto;
    }
}

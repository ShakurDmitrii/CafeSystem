import styles from "./UnitChips.module.css";
import { PURCHASE_UNITS } from "./units";

/** Выбор единицы закупки кнопками: кг, г, л, мл, шт. */
export default function UnitChips({ value, onChange, name = "purchaseUnit", disabled = false, label = "Как покупаете" }) {
    return (
        <fieldset className={styles.unitChips} disabled={disabled}>
            <legend>{label}</legend>
            <div className={styles.chips}>
                {PURCHASE_UNITS.map((option) => (
                    <label key={option.value} className={value === option.value ? styles.chipActive : styles.chip}>
                        <input
                            type="radio"
                            name={name}
                            value={option.value}
                            checked={value === option.value}
                            onChange={() => onChange(option.value)}
                        />
                        {option.label}
                    </label>
                ))}
            </div>
        </fieldset>
    );
}

import { Input } from '../atoms/Input';

/** A label + input pair with an optional validation error, composed from atoms. */
export function FormField({ id, label, error, ...inputProps }) {
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      <Input id={id} {...inputProps} />
      {error && (
        <p role="alert" className="form-field-error">
          {error}
        </p>
      )}
    </div>
  );
}

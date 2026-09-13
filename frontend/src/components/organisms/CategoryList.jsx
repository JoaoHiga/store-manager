export function CategoryList({ categories }) {
  if (categories.length === 0) {
    return <p>No hay categorías todavía.</p>;
  }

  return (
    <ul className="category-list">
      {categories.map((category) => (
        <li key={category.id}>
          <strong>{category.name}</strong>
          {category.description && <span> — {category.description}</span>}
        </li>
      ))}
    </ul>
  );
}

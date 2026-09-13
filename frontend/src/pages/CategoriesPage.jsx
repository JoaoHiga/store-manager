import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { createCategory, listCategories } from '../api/catalogApi';
import { Button } from '../components/atoms/Button';
import { CategoryList } from '../components/organisms/CategoryList';
import { FormField } from '../components/molecules/FormField';

export function CategoriesPage() {
  const queryClient = useQueryClient();
  const {
    data: categories = [],
    isLoading,
    error,
  } = useQuery({ queryKey: ['categories'], queryFn: listCategories });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [formError, setFormError] = useState(null);

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['categories'] });
      setName('');
      setDescription('');
      setFormError(null);
    },
    onError: (mutationError) => setFormError(mutationError.message),
  });

  function handleSubmit(event) {
    event.preventDefault();
    if (!name.trim()) {
      setFormError('El nombre es obligatorio');
      return;
    }
    createMutation.mutate({ name, description: description || null });
  }

  if (isLoading) return <p>Cargando categorías...</p>;
  if (error)
    return (
      <p role="alert">Error al cargar categorías: {error.message}</p>
    );

  return (
    <section>
      <h2>Categorías</h2>
      <form onSubmit={handleSubmit}>
        <FormField id="category-name" label="Nombre" value={name} onChange={(event) => setName(event.target.value)} />
        <FormField
          id="category-description"
          label="Descripción"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
        {formError && <p role="alert">{formError}</p>}
        <Button type="submit" disabled={createMutation.isPending}>
          Crear categoría
        </Button>
      </form>
      <CategoryList categories={categories} />
    </section>
  );
}

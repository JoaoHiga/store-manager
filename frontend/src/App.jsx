import { CategoriesPage } from './pages/CategoriesPage';
import { ProductsPage } from './pages/ProductsPage';
import { RecordSalePage } from './pages/RecordSalePage';
import { SalesListPage } from './pages/SalesListPage';
import { SuppliersPage } from './pages/SuppliersPage';
import './App.css';

function App() {
  return (
    <main>
      <h1>store-manager</h1>
      <CategoriesPage />
      <ProductsPage />
      <SuppliersPage />
      <RecordSalePage />
      <SalesListPage />
    </main>
  );
}

export default App;

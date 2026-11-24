import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <main className="page">
      <section className="card">
        <p>페이지를 찾을 수 없어요.</p>
        <Link to="/">처음으로</Link>
      </section>
    </main>
  )
}

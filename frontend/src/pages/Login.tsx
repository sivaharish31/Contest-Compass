import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../context/AuthContext';
import { useNavigate, Link } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { ApiError } from '../api/client';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type LoginForm = z.infer<typeof schema>;

export function Login() {
  const { login, user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    if (user) navigate('/', { replace: true });
  }, [user, navigate]);

  const onSubmit = async (data: LoginForm) => {
    setError('');
    try {
      await login(data);
      navigate('/');
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        setError('Invalid email or password');
      } else {
        setError('Something went wrong. Please check your credentials and try again.');
      }
    }
  };

  return (
    <div className="mx-auto max-w-md pt-12">
      <div className="bento-card p-8">
        <div className="mb-6 text-center">
          <div
            className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded border"
            style={{ backgroundColor: 'var(--accent-subtle)', borderColor: 'var(--border)' }}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--accent)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <path d="M12 6v6l4 2" />
            </svg>
          </div>
          <h1 className="text-2xl font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
            Welcome back
          </h1>
          <p className="mt-1 text-xs" style={{ color: 'var(--text-muted)' }}>
            Sign in to access your competitive programming analytics
          </p>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {error && (
            <div
              className="rounded px-3 py-2 text-xs font-medium border"
              style={{ backgroundColor: 'var(--danger-muted)', borderColor: 'var(--danger)', color: 'var(--danger)' }}
            >
              {error}
            </div>
          )}

          <div>
            <label className="mb-1.5 block text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
              Email Address
            </label>
            <input
              type="email"
              autoComplete="email"
              placeholder="you@domain.com"
              {...register('email')}
              className="w-full rounded border px-3 py-2 text-xs outline-none transition-colors focus:border-[var(--accent)] font-mono-editorial"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            />
            {errors.email && (
              <p className="mt-1 text-[11px] font-medium" style={{ color: 'var(--danger)' }}>
                {errors.email.message}
              </p>
            )}
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="text-xs font-semibold" style={{ color: 'var(--text-secondary)' }}>
                Password
              </label>
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="text-[11px] text-muted hover:text-[var(--text-primary)]"
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              {...register('password')}
              className="w-full rounded border px-3 py-2 text-xs outline-none transition-colors focus:border-[var(--accent)] font-mono-editorial"
              style={{
                backgroundColor: 'var(--bg-elevated)',
                borderColor: 'var(--border)',
                color: 'var(--text-primary)',
              }}
            />
            {errors.password && (
              <p className="mt-1 text-[11px] font-medium" style={{ color: 'var(--danger)' }}>
                {errors.password.message}
              </p>
            )}
          </div>

          <button
            type="submit"
            disabled={isSubmitting}
            className="btn-editorial w-full rounded py-2 text-xs font-medium disabled:opacity-50"
          >
            {isSubmitting ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs" style={{ color: 'var(--text-muted)' }}>
          Don't have an account?{' '}
          <Link to="/register" style={{ color: 'var(--accent)' }} className="font-semibold hover:underline">
            Create account
          </Link>
        </p>
      </div>
    </div>
  );
}

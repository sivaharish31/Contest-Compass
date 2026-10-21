import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect, useRef } from 'react';
import { useAuth } from '../context/AuthContext';

interface NavItem {
  to: string;
  label: string;
  auth?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { to: '/', label: 'Dashboard' },
  { to: '/contests', label: 'Contests' },
  { to: '/recommended', label: 'Recommended', auth: true },
  { to: '/performance', label: 'Performance', auth: true },
  { to: '/settings', label: 'Settings', auth: true },
];

export function Layout() {
  const { user, loading, logout } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const location = useLocation();

  const visibleItems = NAV_ITEMS.filter((item) => !item.auth || user);

  const handleLogout = async () => {
    setAccountOpen(false);
    await logout();
    navigate('/');
  };

  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!accountOpen) return;
    function handleClick(e: MouseEvent) {
      if (accountRef.current && !accountRef.current.contains(e.target as Node)) {
        setAccountOpen(false);
      }
    }
    function handleKey(e: KeyboardEvent) {
      if (e.key === 'Escape') setAccountOpen(false);
    }
    document.addEventListener('mousedown', handleClick);
    document.addEventListener('keydown', handleKey);
    return () => {
      document.removeEventListener('mousedown', handleClick);
      document.removeEventListener('keydown', handleKey);
    };
  }, [accountOpen]);

  return (
    <div className="min-h-screen w-full overflow-x-hidden" style={{ backgroundColor: 'var(--bg-primary)' }}>
      {/* Top Navigation Bar */}
      <header
        className="sticky top-0 z-40 border-b transition-colors"
        style={{ backgroundColor: 'var(--bg-header)', borderColor: 'var(--border)' }}
      >
        <div className="mx-auto flex h-12 max-w-[1440px] items-center justify-between px-4 sm:px-6 lg:px-8">
          {/* Left: Brand + Nav */}
          <div className="flex items-center gap-6">
            <NavLink to="/" className="flex items-center gap-2.5 shrink-0">
              <div
                className="flex h-7 w-7 items-center justify-center rounded"
                style={{ backgroundColor: 'var(--btn-primary-bg)', color: 'var(--btn-primary-text)' }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10" />
                  <path d="M12 6v6l4 2" />
                </svg>
              </div>
              <span className="text-sm font-bold tracking-tight" style={{ color: 'var(--text-primary)' }}>
                Contest Compass
              </span>
            </NavLink>

            <nav className="hidden md:flex items-center gap-0.5">
              {visibleItems.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.to === '/'}
                  className="relative rounded px-3 py-1.5 text-xs font-medium transition-colors"
                  style={({ isActive }) => ({
                    backgroundColor: isActive ? 'var(--accent-subtle)' : 'transparent',
                    color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                  })}
                >
                  {({ isActive }) => (
                    <>
                      {item.label}
                      {isActive && (
                        <span
                          className="absolute bottom-0 left-3 right-3 h-0.5 rounded-t"
                          style={{ backgroundColor: 'var(--accent)' }}
                        />
                      )}
                    </>
                  )}
                </NavLink>
              ))}
            </nav>
          </div>

          {/* Right: Account + Mobile Toggle */}
          <div className="flex items-center gap-2">
            {/* Mobile hamburger */}
            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="flex h-8 w-8 items-center justify-center rounded border md:hidden transition-colors hover:bg-[var(--bg-hover)]"
              style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
              aria-label="Toggle navigation"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
                {mobileOpen ? (
                  <>
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </>
                ) : (
                  <>
                    <line x1="3" y1="7" x2="21" y2="7" />
                    <line x1="3" y1="12" x2="21" y2="12" />
                    <line x1="3" y1="17" x2="21" y2="17" />
                  </>
                )}
              </svg>
            </button>

            {/* Account controls */}
            {loading ? null : user ? (
              <div className="relative" ref={accountRef}>
                <button
                  type="button"
                  onClick={() => setAccountOpen(!accountOpen)}
                  className="flex h-8 items-center gap-2 rounded border px-2 transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ borderColor: 'var(--border)', color: 'var(--text-secondary)' }}
                  aria-expanded={accountOpen}
                  aria-haspopup="true"
                  aria-label="Account menu"
                >
                  <div
                    className="flex h-5 w-5 items-center justify-center overflow-hidden rounded text-[10px] font-bold shrink-0"
                    style={{ backgroundColor: 'var(--accent-subtle)', color: 'var(--accent)' }}
                  >
                    {user.avatarUrl ? (
                      <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
                    ) : (
                      user.username.charAt(0).toUpperCase()
                    )}
                  </div>
                  <span className="hidden sm:inline text-xs font-medium truncate max-w-[100px]" style={{ color: 'var(--text-primary)' }}>
                    {user.username}
                  </span>
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
                    <polyline points="6 9 12 15 18 9" />
                  </svg>
                </button>

                {accountOpen && (
                  <div
                    className="absolute right-0 top-full mt-1.5 w-48 rounded-md border shadow-lg z-50"
                    style={{ backgroundColor: 'var(--bg-card)', borderColor: 'var(--border)' }}
                  >
                    <div className="px-3.5 py-2.5 border-b" style={{ borderColor: 'var(--border-subtle)' }}>
                      <p className="text-xs font-bold truncate" style={{ color: 'var(--text-primary)' }}>{user.username}</p>
                      <p className="text-[10px] truncate mt-0.5" style={{ color: 'var(--text-muted)' }}>{user.email}</p>
                    </div>
                    <div className="py-1">
                      <NavLink
                        to="/settings"
                        onClick={() => setAccountOpen(false)}
                        className="block px-3.5 py-1.5 text-xs font-medium transition-colors hover:bg-[var(--bg-hover)]"
                        style={{ color: 'var(--text-secondary)' }}
                      >
                        Settings
                      </NavLink>
                      <button
                        type="button"
                        onClick={handleLogout}
                        className="w-full text-left px-3.5 py-1.5 text-xs font-medium transition-colors hover:bg-[var(--bg-hover)]"
                        style={{ color: 'var(--danger)' }}
                      >
                        Log out
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-1.5">
                <NavLink
                  to="/login"
                  className="rounded px-2.5 py-1.5 text-xs font-medium transition-colors hover:bg-[var(--bg-hover)]"
                  style={{ color: 'var(--text-secondary)' }}
                >
                  Log in
                </NavLink>
                <NavLink
                  to="/register"
                  className="btn-editorial rounded px-2.5 py-1.5 text-xs font-semibold"
                >
                  Sign up
                </NavLink>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Mobile slide-over nav */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden">
          <div className="fixed inset-0 bg-black/60" onClick={() => setMobileOpen(false)} />
          <div
            className="relative flex w-64 flex-col justify-between p-4 shadow-xl z-10"
            style={{ backgroundColor: 'var(--bg-card)', borderRight: '1px solid var(--border)' }}
          >
            <div>
              <div className="flex items-center justify-between pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
                <span className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>Contest Compass</span>
                <button onClick={() => setMobileOpen(false)} className="p-1 rounded hover:bg-[var(--bg-hover)]" style={{ color: 'var(--text-muted)' }}>
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
                </button>
              </div>
              <nav className="mt-3 space-y-0.5">
                {visibleItems.map((item) => (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.to === '/'}
                    className="block rounded px-3 py-2 text-xs font-medium transition-colors"
                    style={({ isActive }) => ({
                      backgroundColor: isActive ? 'var(--accent-subtle)' : 'transparent',
                      color: isActive ? 'var(--text-primary)' : 'var(--text-secondary)',
                    })}
                  >
                    {item.label}
                  </NavLink>
                ))}
              </nav>
            </div>
            <div className="pt-3 border-t" style={{ borderColor: 'var(--border)' }}>
              {user ? (
                <div className="space-y-2">
                  <p className="text-xs font-bold truncate" style={{ color: 'var(--text-primary)' }}>{user.username}</p>
                  <button onClick={handleLogout} className="text-xs transition-colors hover:text-[var(--danger)]" style={{ color: 'var(--text-muted)' }}>Log out</button>
                </div>
              ) : (
                <div className="flex flex-col gap-2">
                  <NavLink to="/login" className="rounded text-center py-1.5 text-xs font-medium border" style={{ borderColor: 'var(--border)', color: 'var(--text-primary)' }}>Log in</NavLink>
                  <NavLink to="/register" className="btn-editorial rounded text-center py-1.5 text-xs font-semibold">Sign up</NavLink>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Main content */}
      <main className="mx-auto max-w-[1440px] w-full p-4 sm:p-6 lg:p-8">
        <Outlet />
      </main>
    </div>
  );
}

import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;
  const loginUrlTree = {} as UrlTree;

  beforeEach(() => {
    authServiceSpy = jasmine.createSpyObj('AuthService', ['isLoggedIn']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree']);
    routerSpy.createUrlTree.and.returnValue(loginUrlTree);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  it('permite el acceso cuando el usuario está autenticado', () => {
    authServiceSpy.isLoggedIn.and.returnValue(true);
    const result = TestBed.runInInjectionContext(() => authGuard());
    expect(result).toBeTrue();
  });

  it('redirige a /login cuando el usuario no está autenticado', () => {
    authServiceSpy.isLoggedIn.and.returnValue(false);
    const result = TestBed.runInInjectionContext(() => authGuard());
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(loginUrlTree);
  });
});

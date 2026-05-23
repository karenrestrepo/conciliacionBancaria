import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { LoginComponent } from './login.component';
import { AuthService } from '../../core/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  const mockLoginOk = {
    success: true,
    message: 'OK',
    data: { token: 'tok', rol: 'CONTADOR', email: 'u@test.com', expiresIn: 3600 },
    timestamp: ''
  };

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['login']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [LoginComponent, ReactiveFormsModule, NoopAnimationsModule],
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  describe('validación del formulario', () => {
    it('formulario inválido con campos vacíos', () => {
      expect(component.form.invalid).toBeTrue();
    });

    it('formulario válido con email y contraseña correctos', () => {
      component.form.setValue({ email: 'user@test.com', password: 'pass123' });
      expect(component.form.valid).toBeTrue();
    });

    it('campo email inválido con formato incorrecto', () => {
      component.form.get('email')!.setValue('no-es-email');
      expect(component.form.get('email')!.hasError('email')).toBeTrue();
    });

    it('contraseña requerida cuando está vacía', () => {
      component.form.get('password')!.setValue('');
      component.form.get('password')!.markAsTouched();
      expect(component.form.get('password')!.hasError('required')).toBeTrue();
    });
  });

  describe('onSubmit()', () => {
    it('no llama a login si el formulario es inválido', () => {
      component.onSubmit();
      expect(authSpy.login).not.toHaveBeenCalled();
    });

    it('navega al dashboard tras login exitoso', fakeAsync(() => {
      authSpy.login.and.returnValue(of(mockLoginOk));
      component.form.setValue({ email: 'user@test.com', password: 'pass123' });

      component.onSubmit();
      tick();

      expect(authSpy.login).toHaveBeenCalledOnceWith({ email: 'user@test.com', password: 'pass123' });
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
      expect(component.loading).toBeFalse();
    }));

    it('muestra mensaje de error tras fallo HTTP', fakeAsync(() => {
      authSpy.login.and.returnValue(throwError(() => new Error('Unauthorized')));
      component.form.setValue({ email: 'user@test.com', password: 'wrong' });

      component.onSubmit();
      tick();

      expect(component.errorMsg).toBeTruthy();
      expect(component.loading).toBeFalse();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
    }));

    it('activa loading durante la petición', () => {
      authSpy.login.and.returnValue(of(mockLoginOk));
      component.form.setValue({ email: 'user@test.com', password: 'pass' });

      component.onSubmit();

      expect(component.loading).toBeFalse();
    });
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { ShellComponent } from './shell.component';
import { AuthService } from '../core/auth.service';

describe('ShellComponent', () => {
  let component: ShellComponent;
  let fixture: ComponentFixture<ShellComponent>;
  let authSpy: jasmine.SpyObj<AuthService>;

  beforeEach(async () => {
    authSpy = jasmine.createSpyObj('AuthService', ['getEmail', 'getRol', 'hasRole', 'logout']);
    authSpy.getEmail.and.returnValue('usuario@empresa.com');
    authSpy.getRol.and.returnValue('CONTADOR');

    await TestBed.configureTestingModule({
      imports: [ShellComponent, NoopAnimationsModule, RouterTestingModule],
      providers: [{ provide: AuthService, useValue: authSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(ShellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debe crearse correctamente', () => {
    expect(component).toBeTruthy();
  });

  describe('getInitial()', () => {
    it('retorna la primera letra del email en mayúscula', () => {
      authSpy.getEmail.and.returnValue('usuario@empresa.com');
      expect(component.getInitial()).toBe('U');
    });

    it('retorna "U" cuando no hay email', () => {
      authSpy.getEmail.and.returnValue(null);
      expect(component.getInitial()).toBe('U');
    });
  });

  describe('canCreate()', () => {
    it('retorna true cuando el usuario puede crear conciliaciones', () => {
      authSpy.hasRole.and.returnValue(true);
      expect(component.canCreate()).toBeTrue();
    });

    it('retorna false cuando el usuario no puede crear conciliaciones', () => {
      authSpy.hasRole.and.returnValue(false);
      expect(component.canCreate()).toBeFalse();
    });
  });

  describe('logout()', () => {
    it('delega el logout al AuthService', () => {
      component.logout();
      expect(authSpy.logout).toHaveBeenCalledTimes(1);
    });
  });
});
